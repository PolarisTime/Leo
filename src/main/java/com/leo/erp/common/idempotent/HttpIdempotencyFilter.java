package com.leo.erp.common.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.RateLimitContext;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;

@Component
public class HttpIdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Idempotency-Key";
    public static final String LEGACY_HEADER = "Idempotency-Key";
    static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final int UNPROCESSABLE_ENTITY = HttpStatus.UNPROCESSABLE_ENTITY.value();

    private final HttpIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public HttpIdempotencyFilter(HttpIdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
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

        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        HttpServletRequest replayableRequest = new ReplayableBodyHttpServletRequest(request, body);
        String scopedKey = scopedKey(request, idempotencyKey);
        String fingerprint = fingerprint(request, body);
        HttpIdempotencyService.Decision decision =
                idempotencyService.start(scopedKey, fingerprint, DEFAULT_TTL);
        HttpIdempotencyService.Status status = decision.status();

        if (status == null) {
            throw new NullPointerException("decision.status");
        }
        if (status == HttpIdempotencyService.Status.ACQUIRED) {
            continueRequest(replayableRequest, response, filterChain, scopedKey, fingerprint);
        } else if (status == HttpIdempotencyService.Status.DUPLICATE_PENDING) {
            writeFailure(request, response, "请勿重复提交，请等待当前请求处理完成");
        } else if (status == HttpIdempotencyService.Status.DUPLICATE_COMPLETED) {
            writeSuccess(response);
        } else {
            writeFailure(request, response, "幂等键已用于不同请求，请重新生成幂等键后再提交");
        }
    }

    private boolean shouldEnforce(HttpServletRequest request, String idempotencyKey) {
        return WRITE_METHODS.contains(request.getMethod())
                && idempotencyKey != null
                && !idempotencyKey.isBlank()
                && !isMultipart(request);
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
        try {
            filterChain.doFilter(request, response);
            if (response.getStatus() >= 200 && response.getStatus() < 400) {
                idempotencyService.markCompleted(scopedKey, fingerprint, DEFAULT_TTL);
            } else {
                idempotencyService.release(scopedKey, fingerprint);
            }
        } catch (ServletException | IOException | RuntimeException ex) {
            idempotencyService.release(scopedKey, fingerprint);
            throw ex;
        }
    }

    private void writeFailure(HttpServletRequest request,
                              HttpServletResponse response,
                              String message) throws IOException {
        response.setStatus(UNPROCESSABLE_ENTITY);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.failure(
                ErrorCode.BUSINESS_ERROR,
                message,
                RateLimitContext.current(request)
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private void writeSuccess(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.success("请求已处理"));
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
