package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserRole;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

@Service
public class UserRoleBindingService {

    private static final String ROLE_STATUS_NORMAL = "正常";

    private final UserRoleRepository userRoleRepository;
    private final RoleSettingRepository roleSettingRepository;
    private final SnowflakeIdGenerator idGenerator;

    public UserRoleBindingService(UserRoleRepository userRoleRepository,
                                  RoleSettingRepository roleSettingRepository,
                                  SnowflakeIdGenerator idGenerator) {
        this.userRoleRepository = userRoleRepository;
        this.roleSettingRepository = roleSettingRepository;
        this.idGenerator = idGenerator;
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

    public String joinRoleNames(Collection<RoleSetting> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        return roles.stream()
                .map(RoleSetting::getRoleName)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
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
                String candidate = item == null ? "" : item.trim();
                if (!candidate.isEmpty()) {
                    normalized.add(candidate);
                }
            }
        }
        return new ArrayList<>(normalized);
    }
}
