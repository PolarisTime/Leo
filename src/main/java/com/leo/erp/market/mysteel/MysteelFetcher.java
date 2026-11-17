package com.leo.erp.market.mysteel;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.retry.RetryExecutor;
import com.leo.erp.common.retry.TransientCallException;
import com.leo.erp.market.QuoteNotPublishedException;
import org.springframework.stereotype.Component;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Pattern;
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
    private static final String LOGIN_JUMP_PAGE = "https://jiancai.mysteel.com/";
    private static final Pattern LOGIN_SUCCESS = Pattern.compile("\"result\"\\s*:\\s*\"success\"");
    private static final Pattern LOGIN_FAILURE = Pattern.compile("\"result\"\\s*:\\s*\"(error\\d+)\"");

    private final MysteelProperties properties;
    private final HttpClient httpClient;
    private final RetryExecutor retry;
    /** 当前会话 Cookie；登录成功后原地刷新，避免每篇文章都带旧 Cookie 请求一次。 */
    private volatile String sessionCookie;
    private final Object loginLock = new Object();

    public MysteelFetcher(MysteelProperties properties) {
        this.properties = properties;
        this.sessionCookie = properties.getCookie();
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
                if (MysteelArticleParser.requiresLogin(html)) {
                    if (!hasCredentials()) {
                        throw new BusinessException(ErrorCode.BUSINESS_ERROR, loginRequiredMessage());
                    }
                    loginAndRefreshCookie();
                    html = fetchDirect(url, what);
                }
                if (MysteelArticleParser.requiresLogin(html)) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                            "Mysteel 自动登录后仍返回登录占位页；"
                                    + "请检查账号是否过期/需要验证码，或更新 Cookie");
                }
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

    private String loginRequiredMessage() {
        return "Mysteel 行情页需要登录：请配置 LEO_MARKET_STEEL_QUOTE_USERNAME/PASSWORD "
                + "以自动登录，或配置有效的 LEO_MARKET_STEEL_QUOTE_COOKIE";
    }

    private boolean hasCredentials() {
        return properties.getUsername() != null && !properties.getUsername().isBlank()
                && properties.getPassword() != null && !properties.getPassword().isBlank();
    }

    /**
     * Cookie 失效时用账号密码重新登录一次。
     *
     * <p>登录分两步：先请求 loginJson.jsp 拿 `_login_token`，再跟随 login_success.jsp/setcookie.jsp
     * 拿 `_MSPASS_SESSION`；只有这两个会话 Cookie 同时存在，行情文章才会返回 k/m 加密价格。</p>
     */
    private void loginAndRefreshCookie() {
        synchronized (loginLock) {
            try {
                loginOnce();
            } catch (java.io.IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Mysteel 自动登录失败: " + ex.getMessage());
            }
        }
    }

    private void loginOnce() throws java.io.IOException, InterruptedException {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        HttpClient loginClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(cookieManager)
                .build();

        String loginUrl = properties.getLoginUrl();
        String separator = loginUrl.contains("?") ? "&" : "?";
        String requestUrl = loginUrl + separator
                + "my_username=" + encode(properties.getUsername())
                + "&my_password=" + md5Hex(properties.getPassword())
                + "&callbackJsonp=loginJsn"
                + "&jumpPage=" + encode(LOGIN_JUMP_PAGE)
                + "&site=jiancai"
                + "&my_rememberStatus=true"
                + "&vcode=";
        HttpResponse<String> loginResponse = loginClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(requestUrl))
                        .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", REFERER)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        String body = loginResponse.body() == null ? "" : loginResponse.body();
        if (loginResponse.statusCode() != 200 || !LOGIN_SUCCESS.matcher(body).find()) {
            var failure = LOGIN_FAILURE.matcher(body);
            String code = failure.find() ? failure.group(1) : "unknown";
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Mysteel 登录失败（" + code + "）：账号或密码错误、账号已过期，或需要验证码");
        }

        String successUrl = loginUrl.replace("/loginJson.jsp", "/login_success.jsp")
                + "?callback=" + encode(LOGIN_JUMP_PAGE);
        loginClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(successUrl))
                        .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", REFERER)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        String cookieHeader = cookieManager.getCookieStore().getCookies().stream()
                .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                .distinct()
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
        if (cookieHeader.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Mysteel 登录成功但未取到会话 Cookie");
        }
        this.sessionCookie = cookieHeader;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String md5Hex(String value) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md5.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 不可用", ex);
        }
    }

    private String fetchDirect(String url, String what) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER);
        if (hasCookie()) {
            builder.header("Cookie", sessionCookie);
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
        String cookie = sessionCookie;
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
