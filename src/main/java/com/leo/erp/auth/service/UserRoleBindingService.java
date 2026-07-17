package com.leo.erp.auth.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.CasbinPolicy;
import com.leo.erp.security.permission.CasbinPolicyStore;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RoleSettingRepository;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserRoleBindingService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    private static final String ROLE_STATUS_NORMAL = StatusConstants.NORMAL;

    private final RoleSettingRepository roleSettingRepository;
    private final PermissionService permissionService;
    private final CasbinPolicyStore casbinPolicyStore;

    public UserRoleBindingService(RoleSettingRepository roleSettingRepository,
                                  PermissionService permissionService,
                                  CasbinPolicyStore casbinPolicyStore) {
        this.roleSettingRepository = roleSettingRepository;
        this.permissionService = permissionService;
        this.casbinPolicyStore = casbinPolicyStore;
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
        List<String> roleCodes = roles == null
                ? List.of()
                : roles.stream().map(RoleSetting::getRoleCode).filter(Objects::nonNull).toList();
        casbinPolicyStore.replaceUserRoles(String.valueOf(userId), roleCodes);
    }

    @Transactional
    public void replaceUserRolesWithinCurrentPrincipalBounds(Long userId, Collection<RoleSetting> roles) {
        assertCurrentPrincipalCanBindRoles(userId, roles);
        replaceUserRoles(userId, roles);
    }

    public void assertCurrentPrincipalCanBindRoles(Long targetUserId, Collection<RoleSetting> roles) {
        List<RoleSetting> requestedRoles = roles == null ? List.of() : new ArrayList<>(roles);
        if (requestedRoles.stream().anyMatch(role -> !ROLE_STATUS_NORMAL.equals(role.getStatus()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "禁用角色不能授予用户");
        }
        Optional<SecurityPrincipal> principal = currentPrincipal();
        if (principal.isEmpty() || currentPrincipalIsAdmin()) {
            return;
        }
        if (Objects.equals(principal.get().id(), targetUserId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "不能修改自己的角色集合");
        }
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
        List<String> roleCodes = casbinPolicyStore.findDirectRoleCodes(String.valueOf(userId));
        if (roleCodes.isEmpty()) {
            return List.of();
        }
        List<RoleSetting> boundRoles = roleSettingRepository.findByRoleCodeInAndDeletedFlagFalse(roleCodes);
        if (boundRoles.isEmpty()) {
            return List.of();
        }

        Map<String, RoleSetting> roleMap = new LinkedHashMap<>();
        boundRoles.stream()
                .filter(role -> ROLE_STATUS_NORMAL.equals(role.getStatus()))
                .forEach(role -> roleMap.put(role.getRoleCode(), role));
        List<RoleSetting> resolved = new ArrayList<>(roleCodes.size());
        for (String roleCode : roleCodes) {
            RoleSetting role = roleMap.get(roleCode);
            if (role != null) {
                resolved.add(role);
            }
        }
        return resolved;
    }

    public List<Long> findUserIdsByRole(String roleCode) {
        return casbinPolicyStore.findUserIdsByRole(roleCode);
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
        if (requestedRoles.isEmpty()) {
            return;
        }
        Map<String, Set<String>> currentPermissionMap = permissionService.getUserPermissionMap(principalId);
        List<String> requestedRoleCodes = requestedRoles.stream()
                .map(RoleSetting::getRoleCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (requestedRoleCodes.isEmpty()) {
            return;
        }
        for (List<CasbinPolicy> policies : casbinPolicyStore.findPermissionsByRoles(requestedRoleCodes).values()) {
            for (CasbinPolicy policy : policies) {
                Set<String> allowedActions = currentPermissionMap.get(policy.resource());
                if (allowedActions == null || !allowedActions.contains(policy.action())) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "不能授予超出自身权限范围的角色");
                }
            }
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
