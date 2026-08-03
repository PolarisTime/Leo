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
import java.util.UUID;
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
        // 优先使用 Micrometer/OTel 写入 MDC 的真实 trace id；未采样请求使用 correlation id 兜底。
        String originalMdcTraceId = MDC.get(MDC_KEY);
        String traceId = normalizeTraceId(originalMdcTraceId);
        boolean temporaryMdcTraceId = false;
        if (traceId == null) {
            traceId = normalizeTraceId(request.getHeader(TRACE_ID_HEADER));
            if (traceId == null) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }
            MDC.put(MDC_KEY, traceId);
            temporaryMdcTraceId = true;
        }

        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (temporaryMdcTraceId) {
                if (originalMdcTraceId == null) {
                    MDC.remove(MDC_KEY);
                } else {
                    MDC.put(MDC_KEY, originalMdcTraceId);
                }
            }
        }
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
