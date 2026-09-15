package com.leo.erp.security.permission;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 权限提供者装配。
 *
 * <p>RBAC0 落地后，主实现是 {@code RoleBasedAuthorityProvider}（标注 {@code @Primary}），
 * 按用户角色实时查询权限；{@link GrantAllAuthorityProvider} 仅在不存在任何其他
 * {@link AuthorityProvider} Bean 时注册，作为极简兜底，不再是默认行为。</p>
 */
@Configuration
public class AuthorityProviderConfig {

    @Bean
    @ConditionalOnMissingBean(AuthorityProvider.class)
    public AuthorityProvider grantAllAuthorityProvider() {
        return new GrantAllAuthorityProvider();
    }
}
