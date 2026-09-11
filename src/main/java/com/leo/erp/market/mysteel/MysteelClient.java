package com.leo.erp.market.mysteel;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mysteel 行情 HTTP 抓取: 列表页找文章、下载文章。
 * 触发风控(安全验证页)时抛出业务异常, 由调用方告警并延后重试。
 */
@Slf4j
@Component
public class MysteelClient {

    private static final int MIN_VALID_HTML_LENGTH = 10000;

    /** 列表页缓存(短时有效), 供同一轮批量补数复用, 避免重复请求触发风控。 */
    private static final long LIST_CACHE_MILLIS = 60_000L;
    private volatile String cachedListHtml;
    private volatile long cachedListAt;

    private final MysteelProperties properties;
    private final HttpClient httpClient;

    public MysteelClient(MysteelProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 在列表页查找指定日期最新一篇杭州建筑钢材价格行情文章。 */
    public Optional<String> findLatestArticleUrl(LocalDate date) {
        return findArticleUrls(date).stream().findFirst();
    }

    /** 在列表页查找指定日期当天所有杭州建筑钢材价格行情文章(按列表顺序, 新→旧)。 */
    public List<String> findArticleUrls(LocalDate date) {
        String listHtml = cachedListHtml();
        if (looksLikeRiskControl(listHtml)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "列表页返回安全验证页(疑似触发IP风控), 请稍后再试");
        }
        Pattern dayPattern = Pattern.compile("<a href=\"(https://jiancai\\.mysteel\\.com/m/[^\"]+)\"[^>]*title=\"[^\\\"]*"
                + date.getMonthValue() + "月" + date.getDayOfMonth() + "日[^\\\"]*杭州市场建筑钢材价格行情\"[^>]*>");
        Set<String> urls = new LinkedHashSet<>();
        dayPattern.matcher(listHtml).results().forEach(matcher -> urls.add(matcher.group(1)));
        return new ArrayList<>(urls);
    }

    /** 下载行情文章 HTML。 */
    public String fetchArticle(String articleUrl) {
        return fetch(articleUrl, "行情文章");
    }

    private String cachedListHtml() {
        long now = System.currentTimeMillis();
        String html = cachedListHtml;
        if (html != null && now - cachedListAt < LIST_CACHE_MILLIS) {
            return html;
        }
        String fetched = fetch(properties.getListUrl(), "行情列表页");
        if (!looksLikeRiskControl(fetched)) {
            cachedListHtml = fetched;
            cachedListAt = now;
        }
        return fetched;
    }

    private String fetch(String url, String what) {
        if (properties.getFetchSshHost() != null && !properties.getFetchSshHost().isBlank()) {
            return fetchViaSsh(url, what);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                .header("Referer", "https://hangzhou.mysteel.com/")
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
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
        String safeUrl = url.replace("'", "'\\''");
        // ssh 会把剩余参数拼接为远端 shell 命令, 故整体作为一条命令传入并自行加引号
        String remoteCommand = "curl -sSL --max-time 25 -A '" + userAgent + "'"
                + " -e 'https://hangzhou.mysteel.com/' '" + safeUrl + "'";
        ProcessBuilder builder = new ProcessBuilder(
                "ssh",
                "-o", "BatchMode=yes",
                "-o", "StrictHostKeyChecking=no",
                "-o", "ConnectTimeout=10",
                properties.getFetchSshHost(),
                remoteCommand);
        try {
            Process process = builder.start();
            byte[] out = process.getInputStream().readAllBytes();
            byte[] err = process.getErrorStream().readAllBytes();
            if (!process.waitFor(35, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, what + "远程取数超时");
            }
            String body = new String(out, java.nio.charset.StandardCharsets.UTF_8);
            if (process.exitValue() != 0 || body.isBlank()) {
                String message = new String(err, java.nio.charset.StandardCharsets.UTF_8).trim();
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

    private boolean looksLikeRiskControl(String html) {
        return html.contains("安全验证") || html.length() < MIN_VALID_HTML_LENGTH;
    }
}
