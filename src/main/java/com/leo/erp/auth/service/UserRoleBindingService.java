package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserRole;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.role.domain.entity.RolePermission;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RolePermissionRepository;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class UserRoleBindingService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    private static final String ROLE_STATUS_NORMAL = StatusConstants.NORMAL;

    private final UserRoleRepository userRoleRepository;
    private final RoleSettingRepository roleSettingRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionService permissionService;

    @Autowired
    public UserRoleBindingService(UserRoleRepository userRoleRepository,
                                  RoleSettingRepository roleSettingRepository,
                                  SnowflakeIdGenerator idGenerator,
                                  RolePermissionRepository rolePermissionRepository,
                                  PermissionService permissionService) {
        this.userRoleRepository = userRoleRepository;
        this.roleSettingRepository = roleSettingRepository;
        this.idGenerator = idGenerator;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionService = permissionService;
    }

    public UserRoleBindingService(UserRoleRepository userRoleRepository,
                                  RoleSettingRepository roleSettingRepository,
                                  SnowflakeIdGenerator idGenerator) {
        this(userRoleRepository, roleSettingRepository, idGenerator, null, null);
    }

    @Transactional(readOnly = true)
    public List<RoleSetting> resolveRoles(Collection<String> roleIdentifiers) {
        List<String> normalizedIdentifiers = normalizeIdentifiers(roleIdentifiers);
        if (normalizedIdentifiers.isEmpty()) {
            return List.of();
        }

        Map<String, RoleSetting> resolvedRoleMap = buildResolvedRoleMap(normalizedIdentifiers);
        List<RoleSetting> resolvedRoles = new ArrayList<>(normalizedIdentifiers.size());
        for (String identifier : normalizedIdentifiers) {
            RoleSetting role = resolvedRoleMap.get(identifier.toLowerCase(Locale.ROOT));
            if (role == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色不存在: " + identifier);
            }
            resolvedRoles.add(role);
        }
        return resolvedRoles;
    }

    public List<RoleSetting> resolveRolesByIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = roleIds.stream().filter(id -> id != null).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        List<RoleSetting> roles = roleSettingRepository.findByIdInAndDeletedFlagFalse(distinctIds);
        if (roles.size() != distinctIds.size()) {
            Set<Long> foundIds = roles.stream().map(RoleSetting::getId).collect(java.util.stream.Collectors.toSet());
            List<Long> missing = distinctIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色不存在: " + missing);
        }
        return roles;
    }

    @Transactional
    public void replaceUserRoles(Long userId, Collection<RoleSetting> roles) {
        userRoleRepository.deleteByUserIdAndDeletedFlagFalse(userId);
        userRoleRepository.flush();
        if (roles == null || roles.isEmpty()) {
            return;
        }

        List<UserRole> bindings = new ArrayList<>();
        for (RoleSetting role : roles) {
            UserRole binding = new UserRole();
            binding.setId(idGenerator.nextId());
            binding.setUserId(userId);
            binding.setRoleId(role.getId());
            bindings.add(binding);
        }
        userRoleRepository.saveAll(bindings);
    }

    @Transactional
    public void replaceUserRolesWithinCurrentPrincipalBounds(Long userId, Collection<RoleSetting> roles) {
        assertCurrentPrincipalCanBindRoles(userId, roles);
        replaceUserRoles(userId, roles);
    }

    public void assertCurrentPrincipalCanBindRoles(Long targetUserId, Collection<RoleSetting> roles) {
        Optional<SecurityPrincipal> principal = currentPrincipal();
        if (principal.isEmpty() || currentPrincipalIsAdmin()) {
            return;
        }
        if (Objects.equals(principal.get().id(), targetUserId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "不能修改自己的角色集合");
        }
        List<RoleSetting> requestedRoles = roles == null ? List.of() : new ArrayList<>(roles);
        if (requestedRoles.stream().anyMatch(this::isAdminRole)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "非系统管理员不能授予系统管理员角色");
        }
        assertRolesWithinCurrentPrincipalPermissions(principal.get().id(), requestedRoles);
    }

    public String joinRoleNames(Collection<RoleSetting> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        return roles.stream()
                .map(RoleSetting::getRoleName)
                .distinct()
                .collect(Collectors.joining(","));
    }

    @Transactional(readOnly = true)
    public List<RoleSetting> resolveRolesForUser(Long userId) {
        List<UserRole> bindings = userRoleRepository.findByUserIdAndDeletedFlagFalse(userId);
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = bindings.stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<RoleSetting> boundRoles = roleSettingRepository.findByIdInAndDeletedFlagFalse(roleIds);
        if (boundRoles.isEmpty()) {
            return List.of();
        }

        Map<Long, RoleSetting> roleMap = new LinkedHashMap<>();
        boundRoles.stream()
                .filter(role -> ROLE_STATUS_NORMAL.equals(role.getStatus()))
                .forEach(role -> roleMap.put(role.getId(), role));
        List<RoleSetting> resolved = new ArrayList<>(roleIds.size());
        for (Long roleId : roleIds) {
            RoleSetting role = roleMap.get(roleId);
            if (role != null) {
                resolved.add(role);
            }
        }
        return resolved;
    }

    public List<GrantedAuthority> toGrantedAuthorities(Collection<RoleSetting> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return roles.stream()
                .map(RoleSetting::getRoleCode)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().replace(' ', '_').toUpperCase(Locale.ROOT))
                .distinct()
                .map(value -> new SimpleGrantedAuthority("ROLE_" + value))
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private Map<String, RoleSetting> buildResolvedRoleMap(List<String> normalizedIdentifiers) {
        Map<String, RoleSetting> resolved = new LinkedHashMap<>();
        roleSettingRepository.findByRoleCodeInAndDeletedFlagFalse(normalizedIdentifiers)
                .forEach(role -> resolved.putIfAbsent(role.getRoleCode().trim().toLowerCase(Locale.ROOT), role));
        roleSettingRepository.findByRoleNameInAndDeletedFlagFalse(normalizedIdentifiers)
                .forEach(role -> resolved.putIfAbsent(role.getRoleName().trim().toLowerCase(Locale.ROOT), role));
        return resolved;
    }

    private void assertRolesWithinCurrentPrincipalPermissions(Long principalId, List<RoleSetting> requestedRoles) {
        if (requestedRoles.isEmpty() || rolePermissionRepository == null || permissionService == null) {
            return;
        }
        Map<String, Set<String>> currentPermissionMap = permissionService.getUserPermissionMap(principalId);
        List<Long> requestedRoleIds = requestedRoles.stream()
                .map(RoleSetting::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (requestedRoleIds.isEmpty()) {
            return;
        }
        for (RolePermission permission : rolePermissionRepository.findByRoleIdInAndDeletedFlagFalse(requestedRoleIds)) {
            String resource = permission.getResourceCode();
            String action = permission.getActionCode();
            Set<String> allowedActions = currentPermissionMap.get(resource);
            if (allowedActions == null || !allowedActions.contains(action)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "不能授予超出自身权限范围的角色");
            }
            assertRoleDataScopeWithinCurrentPrincipal(principalId, requestedRoles, permission);
        }
    }

    private void assertRoleDataScopeWithinCurrentPrincipal(Long principalId,
                                                           List<RoleSetting> requestedRoles,
                                                           RolePermission permission) {
        RoleSetting role = requestedRoles.stream()
                .filter(candidate -> Objects.equals(candidate.getId(), permission.getRoleId()))
                .findFirst()
                .orElse(null);
        if (role == null) {
            return;
        }
        String resource = com.leo.erp.security.permission.ResourcePermissionCatalog.normalizeResource(permission.getResourceCode());
        String action = com.leo.erp.security.permission.ResourcePermissionCatalog.normalizeAction(permission.getActionCode());
        String requestedScope = com.leo.erp.security.permission.ResourcePermissionCatalog.normalizeDataScope(role.getDataScope());
        String currentScope = permissionService.getUserDataScope(principalId, resource, action);
        String normalizedCurrentScope = com.leo.erp.security.permission.ResourcePermissionCatalog.normalizeDataScope(currentScope);
        if (!Objects.equals(
                com.leo.erp.security.permission.ResourcePermissionCatalog.broaderDataScope(requestedScope, normalizedCurrentScope),
                normalizedCurrentScope)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "不能授予超出自身数据范围的角色");
        }
    }

    private boolean isAdminRole(RoleSetting role) {
        return role != null && ADMIN_ROLE_CODE.equals(normalizeRoleCode(role.getRoleCode()));
    }

    private String normalizeRoleCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean currentPrincipalIsAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
    }

    private Optional<SecurityPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    private List<String> normalizeIdentifiers(Collection<String> roleIdentifiers) {
        if (roleIdentifiers == null || roleIdentifiers.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String identifier : roleIdentifiers) {
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            for (String item : identifier.split(",")) {
                String candidate = item.trim();
                if (!candidate.isEmpty()) {
                    normalized.add(candidate);
                }
            }
        }
        return new ArrayList<>(normalized);
    }
}
