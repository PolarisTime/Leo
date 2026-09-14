package com.leo.erp.security.permission;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 权限提供者装配：注册默认的“放行全部”实现。
 *
 * <p>{@link ConditionalOnMissingBean} 保证一旦应用中存在自定义 {@link AuthorityProvider}
 * Bean（如将来的角色-权限查询实现），默认实现自动退让，无需修改本类或调用方。</p>
 */
@Configuration
public class AuthorityProviderConfig {

    @Bean
    @ConditionalOnMissingBean(AuthorityProvider.class)
    public AuthorityProvider grantAllAuthorityProvider() {
        return new GrantAllAuthorityProvider();
    }
}
