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
import java.util.Optional;
import java.util.Set;

/**
 * 基于数据库角色-权限的 {@link AuthorityProvider} 主实现（RBAC0）。
 *
 * <p>按登录用户 id 聚合其所有<b>启用</b>角色（{@code status='正常'} 且未删除）的权限码并集。
 * 若集合中包含通配 {@link PermissionCodes#WILDCARD} 或 {@code 资源:*}，由
 * {@code PermissionAuthorizationManager} 的前缀匹配语义负责展开，本类原样返回。</p>
 *
 * <p><strong>缓存策略：</strong>权限集合缓存于 Redis（{@link PermissionCacheService}），
 * 避免每个已认证请求都执行一次三表 join。缓存以「权限版本号纪元」主动失效：任何 RBAC 写入
 * （角色增删改、状态变更、角色权限替换、用户角色替换）提交后都会更换纪元，使全部既有缓存
 * 立即不可达，因此权限变更在下一个请求即生效，语义与原「每请求实时查询」保持一致。
 * 缓存 TTL（{@code leo.redis.auth-user.permission-ttl}，默认 5 分钟）仅作为漏挂失效点时的兜底。</p>
 *
 * <p>系统主体 {@link SecurityPrincipal#system()}（id ≤ 0）视为内部调用，返回全部权限码，不经过缓存。</p>
 */
@Component
@Primary
public class RoleBasedAuthorityProvider implements AuthorityProvider {

    private final SysRolePermissionRepository rolePermissionRepository;
    private final PermissionCacheService permissionCacheService;

    public RoleBasedAuthorityProvider(SysRolePermissionRepository rolePermissionRepository,
                                      PermissionCacheService permissionCacheService) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionCacheService = permissionCacheService;
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
        long credentialVersion = principal.credentialVersion();

        Optional<List<String>> cached = permissionCacheService.get(userId, credentialVersion);
        if (cached.isPresent()) {
            return toAuthoritySet(cached.get());
        }

        List<String> codes = rolePermissionRepository.findPermissionCodesByUserId(userId, StatusConstants.NORMAL);
        permissionCacheService.put(userId, credentialVersion, codes);
        return toAuthoritySet(codes);
    }

    private Set<String> toAuthoritySet(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(codes);
    }
}
