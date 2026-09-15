package com.leo.erp.security.rbac.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.rbac.domain.entity.SysRole;
import com.leo.erp.security.rbac.domain.entity.SysUserRole;
import com.leo.erp.security.rbac.repository.SysRoleRepository;
import com.leo.erp.security.rbac.repository.SysUserRoleRepository;
import com.leo.erp.security.rbac.web.dto.RoleResponse;
import com.leo.erp.security.rbac.web.dto.UserRolesResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * RBAC0 用户-角色服务，支持整体替换用户角色集合。
 *
 * <p>与角色权限一致，授权查询不做缓存，变更在下一请求立即生效。</p>
 */
@Service
public class UserRoleService {

    private final SysUserRoleRepository userRoleRepository;
    private final SysRoleRepository roleRepository;
    private final UserAccountRepository userAccountRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public UserRoleService(SysUserRoleRepository userRoleRepository,
                           SysRoleRepository roleRepository,
                           UserAccountRepository userAccountRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator) {
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.userAccountRepository = userAccountRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Transactional(readOnly = true)
    public UserRolesResponse rolesOf(Long userId) {
        requireActiveUser(userId);
        List<Long> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return new UserRolesResponse(userId, List.of());
        }
        List<SysRole> roles = roleRepository.findByIdInAndDeletedFlagFalse(roleIds);
        return new UserRolesResponse(userId, roles.stream().map(this::toRoleResponse).toList());
    }

    @Transactional
    public UserRolesResponse replaceRoles(Long userId, List<Long> roleIds) {
        requireActiveUser(userId);
        List<Long> distinctRoleIds = roleIds == null
                ? List.of()
                : roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!distinctRoleIds.isEmpty()) {
            List<SysRole> roles = roleRepository.findActiveByIdIn(distinctRoleIds, StatusConstants.NORMAL);
            if (roles.size() != distinctRoleIds.size()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "存在不存在或已禁用的角色");
            }
        }
        userRoleRepository.deleteByUserId(userId);
        if (!distinctRoleIds.isEmpty()) {
            List<SysUserRole> links = distinctRoleIds.stream().map(roleId -> {
                SysUserRole link = new SysUserRole();
                link.setId(snowflakeIdGenerator.nextId());
                link.setUserId(userId);
                link.setRoleId(roleId);
                return link;
            }).toList();
            userRoleRepository.saveAll(links);
        }
        return rolesOf(userId);
    }

    /**
     * 为账号授予内置超级管理员角色（若角色存在且尚未关联）。
     *
     * <p>用于首次初始化创建账号时避免“无任何角色导致全部接口 403”的锁死场景。</p>
     */
    @Transactional
    public void grantSuperAdmin(Long userId) {
        if (userId == null) {
            return;
        }
        Optional<SysRole> superAdmin = roleRepository.findByCodeAndDeletedFlagFalse(SysRole.SUPER_ADMIN_CODE);
        if (superAdmin.isEmpty()) {
            return;
        }
        Long roleId = superAdmin.get().getId();
        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            return;
        }
        SysUserRole link = new SysUserRole();
        link.setId(snowflakeIdGenerator.nextId());
        link.setUserId(userId);
        link.setRoleId(roleId);
        userRoleRepository.save(link);
    }

    private void requireActiveUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户ID不能为空");
        }
        UserAccount account = userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号不存在"));
        if (account.getStatus() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "账号状态异常");
        }
    }

    private RoleResponse toRoleResponse(SysRole role) {
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isBuiltin(),
                role.getStatus()
        );
    }
}
