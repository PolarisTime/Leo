package com.leo.erp.security.rbac;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.AuthorityProvider;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.rbac.repository.SysRolePermissionRepository;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于数据库角色-权限的 {@link AuthorityProvider} 主实现（RBAC0）。
 *
 * <p>按登录用户 id 聚合其所有<b>启用</b>角色（{@code status='正常'} 且未删除）的权限码并集。
 * 若集合中包含通配 {@link PermissionCodes#WILDCARD} 或 {@code 资源:*}，由
 * {@code PermissionAuthorizationManager} 的前缀匹配语义负责展开，本类原样返回。</p>
 *
 * <p><strong>缓存策略：</strong>本实现<b>不缓存</b>权限集合，认证过滤器每请求调用一次，
 * 实时查询数据库。这样角色或权限变更在下一个请求立即生效，无需缓存失效入口；
 * 当前为小规模部署，简单可靠优先。系统主体 {@link SecurityPrincipal#system()}（id ≤ 0）
 * 视为内部调用，返回全部权限码。</p>
 */
@Component
@Primary
public class RoleBasedAuthorityProvider implements AuthorityProvider {

    private final SysRolePermissionRepository rolePermissionRepository;

    public RoleBasedAuthorityProvider(SysRolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    public Collection<String> authoritiesFor(SecurityPrincipal principal) {
        if (principal == null) {
            return Set.of();
        }
        Long userId = principal.id();
        if (userId == null || userId <= 0L) {
            return PermissionCodes.all();
        }
        List<String> codes = rolePermissionRepository.findPermissionCodesByUserId(userId, StatusConstants.NORMAL);
        if (codes.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(codes);
    }
}
