package com.leo.erp.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RateLimitContext {

    public static final String ATTRIBUTE = RateLimitContext.class.getName() + ".snapshot";

    private RateLimitContext() {
    }

    public static void set(HttpServletRequest request, Snapshot snapshot) {
        if (request != null && snapshot != null) {
            request.setAttribute(ATTRIBUTE, snapshot);
        }
    }

    public static Snapshot current() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return current(attributes.getRequest());
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    public static Snapshot current(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object snapshot = request.getAttribute(ATTRIBUTE);
        return snapshot instanceof Snapshot rateLimit ? rateLimit : null;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Snapshot(
            long limit,
            long remaining,
            Long resetSeconds,
            Long retryAfterSeconds
    ) {
        public static Snapshot allowed(long limit, long remaining) {
            return new Snapshot(limit, remaining, null, null);
        }

        public static Snapshot rejected(long limit, long retryAfterSeconds) {
            long retryAfter = Math.max(1L, retryAfterSeconds);
            return new Snapshot(limit, 0, retryAfter, retryAfter);
        }
    }
}
