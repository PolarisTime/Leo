package com.leo.erp.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Positive;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 雪花 ID 字段校验：非空时必须为正整数，且不超过 {@code Long.MAX_VALUE}。
 *
 * <p>雪花 ID 是 63 位非负整数，业务上不存在零或负数。请求体里的关联 ID
 * （客户、项目、结算主体、来源单据明细等）若接受负数，会作为合法外键写入库中，
 * 形成无法通过正常渠道修复的脏数据——这类错误不会像路径参数那样被「查不到」
 * 自然拦截，因此必须在请求体绑定阶段失败。</p>
 *
 * <p>与 {@code @Positive} 的差别仅在于语义命名与统一提示文案：本注解用
 * {@link ReportAsSingleViolation} 保证一个字段只报一条错误，避免同一字段同时
 * 触发 {@code @NotNull} 与 {@code @Positive} 两条信息。</p>
 *
 * <p>可空性不在本注解职责内：需要必填时叠加 {@code @NotNull}。这样可选关联、
 * 以及「创建时为空、更新时带上」的字段可以共用同一注解。</p>
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@Positive
public @interface SnowflakeId {

    String message() default "ID 必须为正整数";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
