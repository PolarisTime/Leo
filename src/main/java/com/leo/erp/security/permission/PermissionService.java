package com.leo.erp.security.permission;

import com.leo.erp.auth.web.dto.ResourcePermissionResponse;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Facade for permission resolution, caching, and menu visibility.
 * Delegates to {@link PermissionResolver}, {@link PermissionCache},
 * and {@link MenuVisibilityService}.
 */
@Service
public class PermissionService {

    private final PermissionResolver resolver;
    private final PermissionCache cache;
    private final MenuVisibilityService menuVisibility;

    @Autowired
    public PermissionService(PermissionResolver resolver,
                             PermissionCache cache,
                             MenuVisibilityService menuVisibility) {
        this.resolver = resolver;
        this.cache = cache;
        this.menuVisibility = menuVisibility;
    }

    protected PermissionService() {
        this.resolver = null;
        this.cache = null;
        this.menuVisibility = null;
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

    public void evictUserCaches(Collection<Long> userIds) {
        for (Long userId : userIds) {
            cache.evict(userId);
        }
    }

    public void evictMetadataCache() {
        // Menu metadata is loaded directly after the NIH cache migration.
    }

    public void evictAllCache() {
        if (cache != null) {
            cache.evictAll();
        }
    }
}
