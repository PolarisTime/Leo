package com.leo.erp.security.permission;

import com.leo.erp.auth.domain.entity.UserRole;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.role.domain.entity.RolePermission;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RolePermissionRepository;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
class PermissionResolver {

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleSettingRepository roleSettingRepository;
    private final PermissionCache cache;

    PermissionResolver(UserRoleRepository userRoleRepository,
                       RolePermissionRepository rolePermissionRepository,
                       RoleSettingRepository roleSettingRepository,
                       PermissionCache cache) {
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleSettingRepository = roleSettingRepository;
        this.cache = cache;
    }

    Map<String, Set<String>> getUserPermissionMap(Long userId) {
        return getUserPermissionSnapshot(userId).permissionMap();
    }

    UserPermissionSnapshot getUserPermissionSnapshot(Long userId) {
        UserPermissionSnapshot cached = cache.read(userId);
        if (cached != null) {
            return cached;
        }

        List<RoleSetting> roles = resolveActiveRoles(userId);
        if (roles.isEmpty()) {
            return new UserPermissionSnapshot(Map.of());
        }

        List<Long> roleIds = roles.stream().map(RoleSetting::getId).filter(Objects::nonNull).distinct().toList();
        Map<String, Set<String>> permissionMap = new LinkedHashMap<>();
        for (RolePermission permission : rolePermissionRepository.findByRoleIdInAndDeletedFlagFalse(roleIds)) {
            String resource = ResourcePermissionCatalog.normalizeResource(permission.getResourceCode());
            String action = ResourcePermissionCatalog.normalizeAction(permission.getActionCode());
            if (!ResourcePermissionCatalog.isAllowed(resource, action)) {
                continue;
            }
            permissionMap.computeIfAbsent(resource, key -> new LinkedHashSet<>()).add(action);
        }

        UserPermissionSnapshot snapshot = new UserPermissionSnapshot(permissionMap);
        cache.write(userId, snapshot);
        return snapshot;
    }

    String getPermissionSummaryForRoles(Collection<RoleSetting> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        List<Long> roleIds = roles.stream()
                .map(RoleSetting::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return "";
        }
        return ResourcePermissionCatalog.buildPermissionSummary(
                rolePermissionRepository.findByRoleIdInAndDeletedFlagFalse(roleIds));
    }

    List<RoleSetting> resolveActiveRoles(Long userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserIdAndDeletedFlagFalse(userId);
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> directRoleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (directRoleIds.isEmpty()) {
            return List.of();
        }

        // Load directly assigned roles + recursively resolve ancestors
        Map<Long, RoleSetting> allRoles = new LinkedHashMap<>();
        var queue = new java.util.ArrayDeque<>(directRoleIds);
        var visited = new java.util.HashSet<Long>();
        while (!queue.isEmpty()) {
            Long roleId = queue.poll();
            if (!visited.add(roleId)) continue;
            roleSettingRepository.findByIdAndDeletedFlagFalse(roleId)
                    .filter(role -> StatusConstants.NORMAL.equals(role.getStatus()))
                    .ifPresent(role -> {
                        allRoles.put(role.getId(), role);
                        if (role.getParentId() != null && !visited.contains(role.getParentId())) {
                            queue.add(role.getParentId());
                        }
                    });
        }

        // Preserve original order: direct roles first, then ancestors
        LinkedHashSet<RoleSetting> ordered = new LinkedHashSet<>();
        for (Long id : directRoleIds) {
            RoleSetting role = allRoles.get(id);
            if (role != null) ordered.add(role);
        }
        for (var entry : allRoles.entrySet()) {
            if (!directRoleIds.contains(entry.getKey())) {
                ordered.add(entry.getValue());
            }
        }
        return List.copyOf(ordered);
    }
}
