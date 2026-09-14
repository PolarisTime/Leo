package com.leo.erp.security.permission;

import com.leo.erp.security.support.SecurityPrincipal;
import java.util.Collection;

/**
 * 默认权限提供者：为任意登录用户返回全部权限码。
 *
 * <p><strong>这是临时实现</strong>：当前系统为单账号模型（登录即管理员），
 * 因此必须让现有用户全都能访问，避免开启方法级安全后破坏既有功能。
 * 它不查询任何角色/权限表，仅为后续接入完整权限系统保留稳定的调用契约。</p>
 *
 * <p>将来接入角色-权限模型时，新增一个实现 {@link AuthorityProvider} 的 Bean
 * （例如 {@code RoleBasedAuthorityProvider}，按用户 id 查询角色与权限码），
 * 并让 {@link AuthorityProviderConfig} 不再创建本默认 Bean 即可。</p>
 */
public class GrantAllAuthorityProvider implements AuthorityProvider {

    @Override
    public Collection<String> authoritiesFor(SecurityPrincipal principal) {
        return PermissionCodes.all();
    }
}
