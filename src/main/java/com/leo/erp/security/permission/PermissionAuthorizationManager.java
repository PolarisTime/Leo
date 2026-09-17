package com.leo.erp.security.permission;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.ClassUtils;

/**
 * {@link RequirePermission} 的授权决策实现。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>未标注 {@link RequirePermission} 的调用直接放行，保证拦截器只影响显式标注的端点；</li>
 *   <li>未认证或认证未通过则拒绝；</li>
 *   <li>拥有 {@link PermissionCodes#WILDCARD} 的超级管理员一律通过；</li>
 *   <li>持有 {@code 资源:*} 资源级通配时，可通过该资源的任意动作权限码；</li>
 *   <li>否则当前主体拥有注解中任意一个权限码即通过（OR 语义）。</li>
 * </ul>
 */
public final class PermissionAuthorizationManager implements AuthorizationManager<MethodInvocation> {

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, MethodInvocation invocation) {
        RequirePermission annotation = findAnnotation(invocation);
        if (annotation == null || annotation.value().length == 0) {
            return new AuthorizationDecision(true);
        }

        Authentication current = authentication.get();
        if (current == null || !current.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        Set<String> granted = new LinkedHashSet<>();
        for (GrantedAuthority authority : current.getAuthorities()) {
            granted.add(authority.getAuthority());
        }
        if (granted.contains(PermissionCodes.WILDCARD)) {
            return new AuthorizationDecision(true);
        }

        boolean allowed = Arrays.stream(annotation.value())
                .anyMatch(required -> isGranted(required, granted));
        return new AuthorizationDecision(allowed);
    }

    /**
     * 判断所需权限码是否被授予。
     *
     * <p>除精确匹配外，支持 {@code 资源:*} 资源级通配：例如持有 {@code sales-returns:*}
     * 即可通过 {@code sales-returns:read}、{@code sales-returns:audit} 等该资源下的任意动作，
     * 也包括 {@code sales-returns:read:amount} 这类字段级权限码。</p>
     */
    private boolean isGranted(String required, Set<String> granted) {
        // 与 service 层 PermissionChecker / 字段序列化器共用同一通配语义，避免行为漂移。
        return PermissionCodes.grants(granted, required);
    }

    private RequirePermission findAnnotation(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        RequirePermission methodAnnotation = AnnotationUtils.findAnnotation(method, RequirePermission.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        Class<?> targetClass = invocation.getThis() == null
                ? method.getDeclaringClass()
                : ClassUtils.getUserClass(invocation.getThis());
        return AnnotationUtils.findAnnotation(targetClass, RequirePermission.class);
    }
}
