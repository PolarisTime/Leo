package com.leo.erp.security.permission;

import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.auth.web.dto.ResourcePermissionResponse;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.system.department.repository.DepartmentRepository;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.menu.repository.MenuRepository;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RolePermissionRepository;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Facade for permission resolution, caching, menu visibility, and department scope.
 * Delegates to {@link PermissionResolver}, {@link PermissionCache},
 * {@link MenuVisibilityService}, and {@link DepartmentScopeResolver}.
 */
@Service
public class PermissionService {

    private final PermissionResolver resolver;
    private final PermissionCache cache;
    private final MenuVisibilityService menuVisibility;
    private final DepartmentScopeResolver departmentScope;

    @org.springframework.beans.factory.annotation.Autowired
    public PermissionService(UserRoleRepository userRoleRepository,
                             RolePermissionRepository rolePermissionRepository,
                             MenuRepository menuRepository,
                             StringRedisTemplate redisTemplate,
                             RoleSettingRepository roleSettingRepository,
                             Optional<RedisJsonCacheSupport> redisJsonCacheSupport,
                             Optional<UserAccountRepository> userAccountRepository,
                             Optional<DepartmentRepository> departmentRepository) {
        this.cache = new PermissionCache(redisTemplate, redisJsonCacheSupport);
        this.resolver = new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache);
        this.menuVisibility = new MenuVisibilityService(menuRepository, redisJsonCacheSupport);
        this.departmentScope = new DepartmentScopeResolver(userAccountRepository, departmentRepository);
    }

    // Backward-compatible constructors for callers that omit optional dependencies
    public PermissionService(UserRoleRepository userRoleRepository,
                             RolePermissionRepository rolePermissionRepository,
                             MenuRepository menuRepository,
                             StringRedisTemplate redisTemplate,
                             RoleSettingRepository roleSettingRepository,
                             Optional<RedisJsonCacheSupport> redisJsonCacheSupport,
                             Optional<UserAccountRepository> userAccountRepository) {
        this(userRoleRepository, rolePermissionRepository, menuRepository, redisTemplate, roleSettingRepository, redisJsonCacheSupport, userAccountRepository, Optional.empty());
    }

    public PermissionService(UserRoleRepository userRoleRepository,
                             RolePermissionRepository rolePermissionRepository,
                             MenuRepository menuRepository,
                             StringRedisTemplate redisTemplate,
                             RoleSettingRepository roleSettingRepository,
                             Optional<RedisJsonCacheSupport> redisJsonCacheSupport) {
        this(userRoleRepository, rolePermissionRepository, menuRepository, redisTemplate, roleSettingRepository, redisJsonCacheSupport, Optional.empty(), Optional.empty());
    }

    public PermissionService(UserRoleRepository userRoleRepository,
                             RolePermissionRepository rolePermissionRepository,
                             MenuRepository menuRepository,
                             StringRedisTemplate redisTemplate,
                             RoleSettingRepository roleSettingRepository) {
        this(userRoleRepository, rolePermissionRepository, menuRepository, redisTemplate, roleSettingRepository, Optional.empty(), Optional.empty(), Optional.empty());
    }

    // --- Public API ---

    public Map<String, Set<String>> getUserPermissionMap(Long userId) {
        return resolver.getUserPermissionSnapshot(userId).permissionMap();
    }

    public List<ResourcePermissionResponse> getUserPermissions(Long userId) {
        return getUserPermissionMap(userId).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ResourcePermissionResponse(entry.getKey(), Set.copyOf(entry.getValue())))
                .toList();
    }

    public Map<String, String> getUserDataScopes(Long userId) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : resolver.getUserPermissionSnapshot(userId).dataScopeByPermission().entrySet()) {
            String resource = PermissionScopeKeyParser.parseResource(entry.getKey());
            if (resource.isBlank()) {
                continue;
            }
            result.merge(resource, entry.getValue(), ResourcePermissionCatalog::broaderDataScope);
        }
        return result;
    }

    public String getUserDataScope(Long userId, String resourceCode) {
        String resource = ResourcePermissionCatalog.normalizeResource(resourceCode);
        return getUserDataScopes(userId).getOrDefault(resource, ResourcePermissionCatalog.SCOPE_SELF);
    }

    public String getUserDataScope(Long userId, String resourceCode, String actionCode) {
        String resource = ResourcePermissionCatalog.normalizeResource(resourceCode);
        String action = ResourcePermissionCatalog.normalizeAction(actionCode);
        return resolver.getUserPermissionSnapshot(userId)
                .dataScopeByPermission()
                .getOrDefault(PermissionScopeKeyParser.key(resource, action), ResourcePermissionCatalog.SCOPE_SELF);
    }

    public Set<Long> getDataScopeOwnerUserIds(Long userId, String scope) {
        return departmentScope.getOwnerUserIds(userId, scope);
    }

    public Set<String> getVisibleMenuCodes(Long userId) {
        return menuVisibility.getVisibleMenuCodes(getUserPermissionMap(userId));
    }

    public boolean can(Long userId, String resourceCode, String actionCode) {
        String resource = ResourcePermissionCatalog.normalizeResource(resourceCode);
        String action = ResourcePermissionCatalog.normalizeAction(actionCode);
        Set<String> actions = getUserPermissionMap(userId).get(resource);
        return actions != null && actions.contains(action);
    }

    public String getPermissionSummaryForRoles(Collection<RoleSetting> roles) {
        return resolver.getPermissionSummaryForRoles(roles);
    }

    public List<Menu> getActiveMenus() {
        return menuVisibility.getActiveMenus();
    }

    // --- Cache eviction ---

    public void evictCache(Long userId) {
        cache.evict(userId);
    }

    public void evictDepartmentUserCache(Long departmentId) {
        departmentScope.evictDepartmentUserCache(departmentId);
    }

    public void evictMetadataCache() {
        cache.evictMetadata(MenuVisibilityService.MENU_CACHE_KEY);
    }

    public void evictAllCache() {
        departmentScope.clearDepartmentUserCache();
        cache.evictMetadata(MenuVisibilityService.MENU_CACHE_KEY);
        cache.evictAll();
    }
}
