package com.leo.erp.common.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要强制幂等键的写接口（方法或控制器类级别）。
 * 命中后若请求未携带 {@link HttpIdempotencyFilter#HEADER}，由拦截器返回 422。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdempotencyRequired {
}
