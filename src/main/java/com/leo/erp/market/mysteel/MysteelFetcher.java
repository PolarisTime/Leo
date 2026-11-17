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
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Mysteel 页面获取传输层: 固定从本机直连 HTTP，不再经跳板机/代理。
 * 只负责"取回 HTML", 不解析、不缓存。
 *
 * <p><b>重试:</b>一次 {@link #fetch} 内部按 {@code leo.market.steel-quote.retry} 配置做有界退避重试,
 * 只重试瞬时失败——IOException 类网络故障、HTTP 429/5xx、风控页(安全验证页)、空响应;
 * HTTP 4xx(除 429)、线程中断等一律立即失败, 不做无谓重试。
 * 重试预算由传输层统一持有, 因此上层不会再叠加一层重试。</p>
 */
@Component
public class MysteelFetcher {

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
                String html = fetchDirect(url, what);
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

    private String fetchDirect(String url, String what) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER);
        if (hasCookie()) {
            builder.header("Cookie", properties.getCookie());
        }
        HttpRequest request = builder.GET().build();
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

    private boolean hasCookie() {
        String cookie = properties.getCookie();
        return cookie != null && !cookie.isBlank();
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
