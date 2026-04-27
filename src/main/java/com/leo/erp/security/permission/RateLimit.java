package com.leo.erp.security.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Apply IP-level rate limiting to a controller method.
 * Rejects requests once the limit is exceeded within the window.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Maximum allowed requests within the window. */
    int maxRequests() default 10;

    /** Window duration. */
    int duration() default 1;

    /** Time unit for the window. */
    TimeUnit timeUnit() default TimeUnit.MINUTES;
}
