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

        boolean allowed = Arrays.stream(annotation.value()).anyMatch(granted::contains);
        return new AuthorizationDecision(allowed);
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
