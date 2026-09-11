package com.leo.erp.common.idempotent;

import com.leo.erp.common.api.ApiErrorResponseWriter;
import com.leo.erp.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

/**
 * 对标注 {@link IdempotencyRequired} 的写接口强制要求幂等键；
 * 缺失时返回 422。multipart 请求不强制要求幂等键，
 * 但携带幂等键时由 {@link HttpIdempotencyFilter} 按文件内容指纹纳入幂等。
 */
@Component
public class IdempotencyRequiredInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ApiErrorResponseWriter errorResponseWriter;

    public IdempotencyRequiredInterceptor(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        boolean required = handlerMethod.hasMethodAnnotation(IdempotencyRequired.class)
                || handlerMethod.getBeanType().isAnnotationPresent(IdempotencyRequired.class);
        if (!required || !WRITE_METHODS.contains(request.getMethod()) || isMultipart(request)) {
            return true;
        }
        if (resolveKey(request) != null) {
            return true;
        }
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.BUSINESS_ERROR,
                "缺少幂等键：该写操作必须携带请求头 " + HttpIdempotencyFilter.HEADER
        );
        return false;
    }

    private String resolveKey(HttpServletRequest request) {
        String key = request.getHeader(HttpIdempotencyFilter.HEADER);
        if (key == null || key.isBlank()) {
            key = request.getHeader(HttpIdempotencyFilter.LEGACY_HEADER);
        }
        return key == null || key.isBlank() ? null : key.trim();
    }

    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null
                && contentType.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }
}
