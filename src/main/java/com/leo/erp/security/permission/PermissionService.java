package com.leo.erp.security.permission;

import com.leo.erp.auth.web.dto.ResourcePermissionResponse;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import org.casbin.jcasbin.main.SyncedEnforcer;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure RBAC facade backed exclusively by jCasbin.
 */
@Service
public class PermissionService {

    private final SyncedEnforcer enforcer;
    private final RbacAuthorizationService rbacAuthorizationService;
    private final MenuVisibilityService menuVisibility;

    public PermissionService(SyncedEnforcer enforcer,
                             RbacAuthorizationService rbacAuthorizationService,
                             MenuVisibilityService menuVisibility) {
        this.enforcer = enforcer;
        this.rbacAuthorizationService = rbacAuthorizationService;
        this.menuVisibility = menuVisibility;
    }

    // --- Public API ---

    public Map<String, Set<String>> getUserPermissionMap(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        Map<String, Set<String>> result = new java.util.LinkedHashMap<>();
        for (List<String> policy : enforcer.getImplicitPermissionsForUser(String.valueOf(userId))) {
            if (policy.size() < 3) {
                continue;
            }
            String resource = ResourcePermissionCatalog.normalizeResource(policy.get(1));
            String action = ResourcePermissionCatalog.normalizeAction(policy.get(2));
            if (ResourcePermissionCatalog.isAllowed(resource, action)) {
                result.computeIfAbsent(resource, key -> new java.util.LinkedHashSet<>()).add(action);
            }
        }
        return result;
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
        return rbacAuthorizationService.check(userId, resourceCode, actionCode);
    }

    public String getPermissionSummaryForRoles(Collection<RoleSetting> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        List<CasbinPolicy> policies = roles.stream()
                .map(RoleSetting::getRoleCode)
                .filter(roleCode -> roleCode != null && !roleCode.isBlank())
                .flatMap(roleCode -> enforcer.getImplicitPermissionsForUser(roleCode).stream())
                .filter(policy -> policy.size() >= 3)
                .map(policy -> new CasbinPolicy(policy.get(1), policy.get(2)))
                .distinct()
                .toList();
        return ResourcePermissionCatalog.buildPermissionSummary(policies);
    }

    public List<Menu> getActiveMenus() {
        return menuVisibility.getActiveMenus();
    }

    // --- Cache eviction ---

    public void evictCache(Long userId) {
        // jCasbin keeps one synchronized in-memory policy model; there is no per-user permission cache.
    }

    public void evictUserCaches(Collection<Long> userIds) {
        // No-op: policy changes reload the synchronized Enforcer after transaction commit.
    }

    public void evictMetadataCache() {
        // Menu metadata is loaded directly after the NIH cache migration.
    }

    public void evictAllCache() {
        enforcer.loadPolicy();
    }
}
