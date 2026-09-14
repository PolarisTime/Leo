package com.leo.erp.security.permission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 端点级授权注解：要求当前认证主体至少拥有 {@link #value()} 中的一个权限码。
 *
 * <p>可标注在控制器方法（精确控制单个端点）或类（模块级默认，方法标注优先覆盖类标注）上。
 * 权限码使用 {@link PermissionCodes} 中的常量或 {@link PermissionCodes#of(String, String)} 拼接。</p>
 *
 * <p>由 {@link PermissionAuthorizationManager} 读取并通过
 * {@link PermissionMethodSecurityConfig} 注册的方法拦截器执行；
 * 鉴权失败抛出 {@code AccessDeniedException}，由 {@code GlobalExceptionHandler} 统一映射为 403。</p>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 允许访问所需的权限码，满足其中任意一个即通过（OR 语义）。 */
    String[] value();
}
