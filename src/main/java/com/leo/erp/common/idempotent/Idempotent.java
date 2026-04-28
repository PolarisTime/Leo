package com.leo.erp.common.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** SpEL expression to derive the idempotency key (e.g. "#request.paymentNo()"). */
    String key() default "";

    /** Time-to-live for the idempotency record. Default 1 hour. */
    long ttlSeconds() default 3600;
}
