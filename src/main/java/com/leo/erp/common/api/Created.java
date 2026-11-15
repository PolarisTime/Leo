package com.leo.erp.common.api;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 创建资源成功（201）的 OpenAPI 声明。
 *
 * <p>只负责 201 的文档声明，<b>不再</b>携带 {@code @ResponseStatus(CREATED)}：201 由
 * {@link ResponseSupport#created(String, Object)} 或 {@code ResponseEntity.created(...)} 的
 * 实际状态码决定，避免「注解 201 但实现返回 200」的双写不一致。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "201", description = "Created")
public @interface Created {
}
