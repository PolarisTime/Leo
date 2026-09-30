package com.leo.erp.common.idempotent;

import com.leo.erp.common.api.ApiErrorResponseWriter;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.support.SecurityPrincipal;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class HttpIdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Idempotency-Key";
    public static final String LEGACY_HEADER = "Idempotency-Key";
    public static final long MULTIPART_MAX_IDEMPOTENT_BYTES = 5L * 1024 * 1024;
    static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> REPLAYABLE_RESPONSE_HEADERS = Set.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CONTENT_DISPOSITION,
            HttpHeaders.LOCATION,
            HttpHeaders.ETAG,
            HttpHeaders.CACHE_CONTROL
    );
    private final HttpIdempotencyService idempotencyService;
    private final ApiErrorResponseWriter errorResponseWriter;

    public HttpIdempotencyFilter(HttpIdempotencyService idempotencyService,
                                 ApiErrorResponseWriter errorResponseWriter) {
        this.idempotencyService = idempotencyService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String idempotencyKey = resolveIdempotencyKey(request);
        if (!shouldEnforce(request, idempotencyKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpServletRequest replayableRequest = request;
        String fingerprint;
        if (isMultipart(request)) {
            try {
                fingerprint = multipartFingerprint(request);
            } catch (IOException | ServletException ex) {
                log.warn("Failed to fingerprint multipart request, skip HTTP idempotency: path="
                        + normalizedPath(request), ex);
                filterChain.doFilter(request, response);
                return;
            }
        } else {
            byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
            replayableRequest = new ReplayableBodyHttpServletRequest(request, body);
            fingerprint = fingerprint(request, body);
        }
        String scopedKey = scopedKey(request, idempotencyKey);
        HttpIdempotencyService.Decision decision =
                idempotencyService.start(scopedKey, fingerprint, DEFAULT_TTL);
        HttpIdempotencyService.Status status = decision.status();

        if (status == null) {
            throw new NullPointerException("decision.status");
        }
        if (status == HttpIdempotencyService.Status.ACQUIRED) {
            continueRequest(replayableRequest, response, filterChain, scopedKey, fingerprint);
        } else if (status == HttpIdempotencyService.Status.DUPLICATE_PENDING) {
            writeConflict(request, response, "请勿重复提交，请等待当前请求处理完成");
        } else if (status == HttpIdempotencyService.Status.DUPLICATE_COMPLETED) {
            replayCompletedResponse(request, response, decision.response());
        } else if (status == HttpIdempotencyService.Status.UNAVAILABLE) {
            writeUnavailable(request, response);
        } else {
            writeConflict(request, response, "幂等键已用于不同请求，请重新生成幂等键后再提交");
        }
    }

    private boolean shouldEnforce(HttpServletRequest request, String idempotencyKey) {
        if (!WRITE_METHODS.contains(request.getMethod())
                || idempotencyKey == null
                || idempotencyKey.isBlank()) {
            return false;
        }
        if (isMultipart(request) && !isWithinMultipartLimit(request)) {
            log.info("Skip HTTP idempotency for oversized multipart request: path="
                    + normalizedPath(request) + ", contentLength=" + request.getContentLengthLong());
            return false;
        }
        return true;
    }

    private boolean isWithinMultipartLimit(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        return contentLength >= 0 && contentLength <= MULTIPART_MAX_IDEMPOTENT_BYTES;
    }

    private String resolveIdempotencyKey(HttpServletRequest request) {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            key = request.getHeader(LEGACY_HEADER);
        }
        return key == null ? null : key.trim();
    }

    private void continueRequest(HttpServletRequest request,
                                 HttpServletResponse response,
                                 FilterChain filterChain,
                                 String scopedKey,
                                 String fingerprint) throws ServletException, IOException {
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, responseWrapper);
            if (responseWrapper.getStatus() >= 200 && responseWrapper.getStatus() < 400) {
                boolean completed = idempotencyService.markCompleted(
                        scopedKey,
                        fingerprint,
                        DEFAULT_TTL,
                        cachedResponse(responseWrapper)
                );
                if (!completed) {
                    log.error("Failed to persist completed HTTP idempotency response: key=" + scopedKey);
                }
            } else if (responseWrapper.getStatus() >= 400 && responseWrapper.getStatus() < 500) {
                idempotencyService.release(scopedKey, fingerprint);
            } else {
                log.error("HTTP idempotency pending key retained after server error: key=" + scopedKey
                        + ", status=" + responseWrapper.getStatus());
            }
            responseWrapper.copyBodyToResponse();
        } catch (ServletException | IOException | RuntimeException ex) {
            log.error("HTTP idempotency pending key retained after request failure: key=" + scopedKey, ex);
            throw ex;
        }
    }

    /**
     * 幂等键冲突：同一幂等键的请求正在处理中，或该键已被用于不同请求体。
     *
     * <p>语义上这是**冲突**而不是「不可处理的语义错误」：同一幂等键代表同一资源的同一提交，
     * 冲突双方争夺的是同一个业务动作。原先返回 {@code 422 + 4220}（BUSINESS_ERROR），
     * 与「字段校验失败」同码，客户端无法区分「参数不对」与「重复提交」，
     * 也无法按统一的重试语义处理（409 更符合 REST 惯例与本项目规范）。</p>
     *
     * <p>错误码用专用的 {@link ErrorCode#IDEMPOTENCY_CONFLICT}（4092）而不是
     * {@link ErrorCode#CONCURRENT_MODIFICATION}（4090）：4090 的文案引导用户「刷新后重试」，
     * 而重复提交要引导「等待当前请求完成」，前端据此渲染不同提示。</p>
     *
     * <p>同时补上结构化日志：本响应由 {@link ApiErrorResponseWriter} 直接写出、
     * **绕过全局异常处理器**，因此过去完全没有日志——压测报告里「幂等冲突静默无痕」
     * 正是这个原因，导致一次误判（把 422 当成编码失效）。</p>
     */
    private void writeConflict(HttpServletRequest request,
                               HttpServletResponse response,
                               String message) throws IOException {
        log.warn("幂等键冲突，返回 409: method={}, uri={}, keyHash={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                keyFingerprint(request),
                message);
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.CONFLICT,
                ErrorCode.IDEMPOTENCY_CONFLICT,
                message
        );
    }

    /** 只记录幂等键的摘要，避免把客户端令牌原文写进日志。 */
    private String keyFingerprint(HttpServletRequest request) {
        String key = resolveIdempotencyKey(request);
        if (key == null || key.isBlank()) {
            return "-";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException ex) {
            return "-";
        }
    }

    private void writeUnavailable(HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {
        log.warn("幂等依赖服务不可用，返回 503: method={}, uri={}", request.getMethod(), request.getRequestURI());
        // 依赖不可用必须用 5030（service-unavailable），不能用 5000（internal-error）：
        // 否则监控无法区分「Redis 挂了」与「代码有问题」。
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SERVICE_UNAVAILABLE,
                "幂等服务暂不可用，请稍后重试"
        );
    }

    private HttpIdempotencyService.CachedResponse cachedResponse(ContentCachingResponseWrapper response) {
        Map<String, String> headers = new LinkedHashMap<>();
        REPLAYABLE_RESPONSE_HEADERS.forEach(headerName -> {
            String value = response.getHeader(headerName);
            if (value != null && !value.isBlank()) {
                headers.put(headerName, value);
            }
        });
        return new HttpIdempotencyService.CachedResponse(
                response.getStatus(),
                response.getHeader(HttpHeaders.CONTENT_TYPE),
                Base64.getEncoder().encodeToString(response.getContentAsByteArray()),
                headers
        );
    }

    private void replayCompletedResponse(HttpServletRequest request,
                                         HttpServletResponse response,
                                         HttpIdempotencyService.CachedResponse cachedResponse) throws IOException {
        if (cachedResponse == null) {
            writeUnavailable(request, response);
            return;
        }
        response.setStatus(cachedResponse.status());
        if (cachedResponse.contentType() != null) {
            response.setHeader(HttpHeaders.CONTENT_TYPE, cachedResponse.contentType());
        }
        cachedResponse.headers().forEach(response::setHeader);
        response.getOutputStream().write(cachedResponse.body());
    }

    private String scopedKey(HttpServletRequest request, String idempotencyKey) {
        String raw = principalScope() + "\n"
                + request.getMethod() + "\n"
                + normalizedPath(request) + "\n"
                + idempotencyKey;
        return sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String fingerprint(HttpServletRequest request, byte[] body) {
        String raw = request.getMethod() + "\n"
                + normalizedPath(request) + "\n"
                + queryStringOrEmpty(request.getQueryString()) + "\n"
                + sha256Hex(body);
        return sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String multipartFingerprint(HttpServletRequest request) throws ServletException, IOException {
        Collection<Part> parts = request.getParts();
        List<String> descriptors = new ArrayList<>(parts.size());
        for (Part part : parts) {
            descriptors.add(part.getName()
                    + '\n' + part.getSubmittedFileName()
                    + '\n' + part.getSize()
                    + '\n' + sha256Hex(part.getInputStream().readAllBytes()));
        }
        Collections.sort(descriptors);
        String raw = request.getMethod() + "\n"
                + normalizedPath(request) + "\n"
                + queryStringOrEmpty(request.getQueryString()) + "\n"
                + String.join("\n", descriptors);
        return sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizedPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private String queryStringOrEmpty(String queryString) {
        return queryString == null ? "" : queryString;
    }

    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private String principalScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityPrincipal securityPrincipal) {
            return "user:" + securityPrincipal.id();
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? "authenticated" : "auth:" + name;
    }

    private String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
