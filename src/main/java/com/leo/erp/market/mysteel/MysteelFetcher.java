package com.leo.erp.market.mysteel;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Mysteel 页面获取传输层: 默认直连 HTTP; 配置了跳板机则经 SSH 远程 curl。
 * 只负责"取回 HTML", 不解析、不缓存。
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

    public MysteelFetcher(MysteelProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 取回页面 HTML。 */
    public String fetch(String url, String what) {
        return usesSsh() ? fetchViaSsh(url, what) : fetchDirect(url, what);
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
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, what + "响应异常: HTTP " + response.statusCode());
            }
            return body;
        } catch (java.io.IOException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, what + "请求失败: " + ex.getMessage());
        } catch (InterruptedException ex) {
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
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, what + "远程取数超时");
            }
            String body = new String(out, StandardCharsets.UTF_8);
            if (process.exitValue() != 0 || body.isBlank()) {
                String message = new String(err, StandardCharsets.UTF_8).trim();
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        what + "远程取数失败: exit " + process.exitValue()
                                + (message.isEmpty() ? "" : (": " + message)));
            }
            return body;
        } catch (java.io.IOException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, what + "远程取数异常: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, what + "远程取数被中断");
        }
    }

    /** 是否命中站点风控(安全验证页)。 */
    public boolean looksLikeRiskControl(String html) {
        return html != null && html.contains(RISK_CONTROL_MARKER);
    }
}
