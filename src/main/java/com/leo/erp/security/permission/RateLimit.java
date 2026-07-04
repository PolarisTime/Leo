package com.leo.erp.security.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Token-bucket rate limiting on controller methods.
 * Priority chain: API Key → User → IP (each dimension checked independently).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Token refill rate per second. -1 to use dimension-specific default. */
    double rate() default -1;

    /** Maximum burst capacity. -1 to use dimension-specific default. */
    int capacity() default -1;

    /** Tokens consumed per request (e.g. 5 for file upload). */
    int tokens() default 1;
}
