package com.leo.erp.security.permission;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

@Component
public class RateLimitHeaderWriter {

    static final String RETRY_AFTER = "Retry-After";
    static final String LIMIT = "X-RateLimit-Limit";
    static final String REMAINING = "X-RateLimit-Remaining";
    static final String RESET = "X-RateLimit-Reset";

    public void writeAllowed(HttpServletResponse response, long limit, long remaining) {
        if (response == null) {
            return;
        }
        response.setHeader(LIMIT, String.valueOf(limit));
        response.setHeader(REMAINING, String.valueOf(Math.max(0, remaining)));
    }

    public void writeRejected(HttpServletResponse response, long limit, long retryAfterSeconds) {
        if (response == null) {
            return;
        }
        long retryAfter = Math.max(1, retryAfterSeconds);
        response.setHeader(RETRY_AFTER, String.valueOf(retryAfter));
        response.setHeader(LIMIT, String.valueOf(limit));
        response.setHeader(REMAINING, "0");
        response.setHeader(RESET, String.valueOf(retryAfter));
    }
}
