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
import java.util.stream.Collectors;

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
            return new UserPermissionSnapshot(Map.of(), Map.of());
        }

        Map<Long, String> dataScopeByRoleId = roles.stream()
                .collect(Collectors.toMap(RoleSetting::getId, role -> ResourcePermissionCatalog.normalizeDataScope(role.getDataScope())));
        List<Long> roleIds = roles.stream().map(RoleSetting::getId).filter(Objects::nonNull).distinct().toList();
        Map<String, Set<String>> permissionMap = new LinkedHashMap<>();
        Map<String, String> dataScopeByPermission = new LinkedHashMap<>();
        for (RolePermission permission : rolePermissionRepository.findByRoleIdInAndDeletedFlagFalse(roleIds)) {
            String resource = ResourcePermissionCatalog.normalizeResource(permission.getResourceCode());
            String action = ResourcePermissionCatalog.normalizeAction(permission.getActionCode());
            if (!ResourcePermissionCatalog.isAllowed(resource, action)) {
                continue;
            }
            String dataScope = dataScopeByRoleId.getOrDefault(permission.getRoleId(), ResourcePermissionCatalog.SCOPE_SELF);
            permissionMap.computeIfAbsent(resource, key -> new LinkedHashSet<>()).add(action);
            dataScopeByPermission.merge(PermissionScopeKeyParser.key(resource, action), dataScope, ResourcePermissionCatalog::broaderDataScope);
        }

        UserPermissionSnapshot snapshot = new UserPermissionSnapshot(permissionMap, dataScopeByPermission);
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
        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, RoleSetting> activeRoleMap = new LinkedHashMap<>();
        roleSettingRepository.findByIdInAndDeletedFlagFalse(roleIds).stream()
                .filter(role -> StatusConstants.NORMAL.equals(role.getStatus()))
                .forEach(role -> activeRoleMap.put(role.getId(), role));
        return roleIds.stream()
                .map(activeRoleMap::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
