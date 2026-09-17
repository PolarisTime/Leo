package com.leo.erp.security.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注响应字段所需的字段级权限码。
 *
 * <p>配合 {@link PermissionDecimalSerializer}：当前用户不具备该权限码时，
 * 该数值字段序列化为 {@code null}；支持全局通配 {@code *} 与资源级 {@code 资源:*}。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface PermissionField {

    /** 读取该字段所需的字段级权限码，例如 {@code sales-orders:read:amount}。 */
    String value();
}
