package com.leo.erp.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
@Order(TraceIdFilter.TRACE_ID_FILTER_ORDER)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    static final int TRACE_ID_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 2;
    static final String MDC_KEY = "traceId";
    private static final int MAX_TRACE_ID_LENGTH = 128;
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._:-]+");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 主路径：优先回显 Micrometer/OTel 写入 MDC 的 trace id，保证响应头与日志、链路系统一致
        String existingTraceId = normalizeTraceId(MDC.get(MDC_KEY));
        if (existingTraceId != null) {
            response.setHeader(TRACE_ID_HEADER, existingTraceId);
            filterChain.doFilter(request, response);
            return;
        }

        // legacy 兜底：仅当 MDC 无 trace id（如未采样/filter 早于 span 创建）时，回显调用方自带的
        // X-Trace-Id，作为兼容用的 correlation id，不视为主 trace 来源，也不写入 MDC / 创建 span
        String traceId = normalizeTraceId(request.getHeader(TRACE_ID_HEADER));
        if (traceId != null) {
            response.setHeader(TRACE_ID_HEADER, traceId);
        }
        filterChain.doFilter(request, response);
    }

    private static String normalizeTraceId(String traceId) {
        if (traceId == null) {
            return null;
        }
        String normalized = traceId.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_TRACE_ID_LENGTH) {
            return null;
        }
        return SAFE_TRACE_ID.matcher(normalized).matches() ? normalized : null;
    }
}
