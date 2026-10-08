package com.leo.erp.market.mysteel;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.retry.RetryExecutor;
import com.leo.erp.common.retry.TransientCallException;
import com.leo.erp.market.QuoteNotPublishedException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Mysteel 页面获取传输层: 默认直连 HTTP; 配置了跳板机则经 SSH 远程 curl。
 * 只负责"取回 HTML", 不解析、不缓存。
 *
 * <p><b>重试:</b>一次 {@link #fetch} 内部按 {@code leo.market.steel-quote.retry} 配置做有界退避重试,
 * 只重试瞬时失败——IOException 类网络故障、HTTP 429/5xx、风控页(安全验证页)、空响应;
 * HTTP 4xx(除 429)、进程无法启动、线程中断等一律立即失败, 不做无谓重试。
 * 重试预算由传输层统一持有, 因此上层不会再叠加一层重试。</p>
 */
@Component
public class MysteelFetcher {

    /** SSH 远端 curl 完成后允许的额外等待秒数(连接/传输开销)。 */
    private static final long SSH_WAIT_GRACE_SECONDS = 10;

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final String REFERER = "https://hangzhou.mysteel.com/";
    private static final String RISK_CONTROL_MARKER = "安全验证";

    private final MysteelProperties properties;
    private final HttpClient httpClient;
    private final RetryExecutor retry;

    public MysteelFetcher(MysteelProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.retry = RetryExecutor.from(properties.getRetry());
    }

    /** 取回页面 HTML。 */
    public String fetch(String url, String what) {
        return fetch(url, what, null);
    }

    /**
     * 取回页面 HTML，并用调用方提供的内容校验参与重试。
     *
     * <p>传输层只认识「空响应 / 风控页」这类与页面无关的无效结果；列表页「长度不足疑似被截断」
     * 这类校验依赖具体页面语义，由调用方以 {@code contentCheck} 注入，校验不通过时抛出
     * {@link TransientCallException}。</p>
     *
     * <p>校验在传输层的重试预算内执行，因此调用方不需要（也不应该）在自身循环里再包一层重试，
     * 否则最坏尝试次数会相乘放大。</p>
     */
    public String fetch(String url, String what, Consumer<String> contentCheck) {
        try {
            return retry.execute(what, () -> {
                String html = usesSsh() ? fetchViaSsh(url, what) : fetchDirect(url, what);
                if (looksLikeRiskControl(html)) {
                    throw new TransientCallException(what + "返回安全验证页(疑似触发IP风控), 请稍后再试");
                }
                if (html.isBlank()) {
                    throw new TransientCallException(what + "返回空内容");
                }
                if (contentCheck != null) {
                    contentCheck.accept(html);
                }
                return html;
            });
        } catch (TransientCallException ex) {
            // 重试预算用尽(或退避等待被中断): 对上层仍是业务异常, HTTP 契约不变。
            if (ex.getReason() == TransientCallException.Reason.CONTENT_EMPTY) {
                throw new QuoteNotPublishedException(ex.getMessage());
            }
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, ex.getMessage());
        }
    }

    private boolean usesSsh() {
        String host = properties.getFetchSshHost();
        return host != null && !host.isBlank();
    }

    private String fetchDirect(String url, String what) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            if (response.statusCode() != 200) {
                throw statusFailure(what, response.statusCode());
            }
            return body;
        } catch (java.io.IOException ex) {
            throw new TransientCallException(what + "请求失败: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            // 线程被中断属于主动停止, 不重试。
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, what + "请求被中断");
        }
    }

    /** 经跳板机 SSH 远程 curl 取数, 用于绕过本机 IP 风控。 */
    private String fetchViaSsh(String url, String what) {
        String safeUrl = url.replace("'", "'\\''");
        // ssh 会把剩余参数拼接为远端 shell 命令, 故整体作为一条命令传入并自行加引号
        long timeoutSeconds = Math.max(1, properties.getRequestTimeoutMs() / 1000);
        String remoteCommand = "curl -sSL --max-time " + timeoutSeconds
                + " -A '" + USER_AGENT + "'"
                + " -e '" + REFERER + "' '" + safeUrl + "'";
        List<String> command = List.of(
                "ssh",
                "-o", "BatchMode=yes",
                "-o", "StrictHostKeyChecking=no",
                "-o", "ConnectTimeout=10",
                properties.getFetchSshHost(),
                remoteCommand);
        try {
            Process process = new ProcessBuilder(command).start();
            byte[] out = process.getInputStream().readAllBytes();
            byte[] err = process.getErrorStream().readAllBytes();
            if (!process.waitFor(timeoutSeconds + SSH_WAIT_GRACE_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new TransientCallException(what + "远程取数超时");
            }
            String body = new String(out, StandardCharsets.UTF_8);
            if (process.exitValue() != 0 || body.isBlank()) {
                String message = new String(err, StandardCharsets.UTF_8).trim();
                throw new TransientCallException(what + "远程取数失败: exit " + process.exitValue()
                        + (message.isEmpty() ? "" : (": " + message)));
            }
            return body;
        } catch (java.io.IOException ex) {
            // ssh 进程无法启动(如未安装/无权限)属于配置问题, 重试无益。
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, what + "远程取数异常: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, what + "远程取数被中断");
        }
    }

    /** 非 200 响应: 429 与 5xx 视为瞬时失败可重试, 其余为确定性失败。 */
    private RuntimeException statusFailure(String what, int status) {
        String message = what + "响应异常: HTTP " + status;
        return isRetryableStatus(status)
                ? new TransientCallException(message)
                : new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }

    /** 该 HTTP 状态是否值得重试(限流与对端故障)。 */
    static boolean isRetryableStatus(int status) {
        return status == 429 || status >= 500;
    }

    /** 是否命中站点风控(安全验证页)。 */
    public boolean looksLikeRiskControl(String html) {
        return html != null && html.contains(RISK_CONTROL_MARKER);
    }
}
