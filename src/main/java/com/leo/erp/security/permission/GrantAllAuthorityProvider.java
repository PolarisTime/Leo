package com.leo.erp.security.permission;

import com.leo.erp.security.support.SecurityPrincipal;
import java.util.Collection;

/**
 * 兜底权限提供者：为任意登录用户返回全部权限码。
 *
 * <p><strong>不是默认实现</strong>：RBAC0 落地后主实现为 {@code RoleBasedAuthorityProvider}
 * （{@code @Primary}），按用户角色查询权限；系统主体由主实现返回全部权限码。
 * 本类仅在应用未注册任何其他 {@link AuthorityProvider} Bean 时由
 * {@link AuthorityProviderConfig} 兜底注册，避免极端情况下完全没有 Provider。</p>
 */
public class GrantAllAuthorityProvider implements AuthorityProvider {

    @Override
    public Collection<String> authoritiesFor(SecurityPrincipal principal) {
        return PermissionCodes.all();
    }
}
