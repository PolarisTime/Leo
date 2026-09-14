package com.leo.erp.security.permission;

import java.lang.reflect.Method;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 开启方法级安全并注册 {@link RequirePermission} 的授权拦截器。
 *
 * <p>开启 {@code @EnableMethodSecurity} 后，Spring Security 的 {@code @PreAuthorize}、
 * {@code @PostAuthorize} 等标准注解同时可用；本类额外注册一个前置拦截器，
 * 让自定义的 {@link RequirePermission} 也能参与授权决策。</p>
 *
 * <p>拦截器仅在类或方法标注了 {@link RequirePermission} 时生效（自定义切点做 OR 匹配），
 * 不改变未标注端点的既有行为。</p>
 */
@Configuration
@EnableMethodSecurity
public class PermissionMethodSecurityConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static AuthorizationManagerBeforeMethodInterceptor requirePermissionMethodInterceptor() {
        AuthorizationManagerBeforeMethodInterceptor interceptor =
                new AuthorizationManagerBeforeMethodInterceptor(
                        requirePermissionPointcut(),
                        new PermissionAuthorizationManager()
                );
        interceptor.setOrder(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder() + 1);
        return interceptor;
    }

    private static StaticMethodMatcherPointcut requirePermissionPointcut() {
        return new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return AnnotationUtils.findAnnotation(method, RequirePermission.class) != null
                        || AnnotationUtils.findAnnotation(targetClass, RequirePermission.class) != null;
            }
        };
    }
}
