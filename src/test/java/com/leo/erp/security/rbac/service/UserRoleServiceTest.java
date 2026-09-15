package com.leo.erp.security.rbac.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.rbac.domain.entity.SysRole;
import com.leo.erp.security.rbac.domain.entity.SysUserRole;
import com.leo.erp.security.rbac.repository.SysRolePermissionRepository;
import com.leo.erp.security.rbac.repository.SysRoleRepository;
import com.leo.erp.security.rbac.repository.SysUserRoleRepository;
import com.leo.erp.security.rbac.web.dto.RoleResponse;
import com.leo.erp.security.rbac.web.dto.UserRolesResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 用户-角色服务测试：查询映射、不存在/禁用角色校验、整体替换、超级管理员授予幂等。
 */
@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock
    private SysUserRoleRepository userRoleRepository;

    @Mock
    private SysRoleRepository roleRepository;

    @Mock
    private SysRolePermissionRepository rolePermissionRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private UserRoleService userRoleService;

    @Test
    void rolesOf_shouldMapAssignedRoles() {
        when(userAccountRepository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(account(7L)));
        when(userRoleRepository.findByUserId(7L)).thenReturn(List.of(link(1L, 7L, 10L), link(2L, 7L, 11L)));
        when(roleRepository.findByIdInAndDeletedFlagFalse(any()))
                .thenReturn(List.of(role(10L, "A", false), role(11L, "B", false)));

        UserRolesResponse response = userRoleService.rolesOf(7L);

        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.roles()).extracting(RoleResponse::code).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void rolesOf_shouldRejectMissingAccount() {
        when(userAccountRepository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userRoleService.rolesOf(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号不存在");
    }

    @Test
    void replaceRoles_shouldRejectUnknownOrDisabledRole() {
        when(userAccountRepository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(account(7L)));
        when(roleRepository.findActiveByIdIn(eq(List.of(10L, 11L)), eq(StatusConstants.NORMAL)))
                .thenReturn(List.of(role(10L, "A", false)));

        assertThatThrownBy(() -> userRoleService.replaceRoles(7L, List.of(10L, 11L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已禁用");

        verify(userRoleRepository, never()).deleteByUserId(any());
    }

    @Test
    void replaceRoles_shouldReplaceWholeSet() {
        when(userAccountRepository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(account(7L)));
        when(roleRepository.findActiveByIdIn(eq(List.of(10L)), eq(StatusConstants.NORMAL)))
                .thenReturn(List.of(role(10L, "A", false)));
        when(snowflakeIdGenerator.nextId()).thenReturn(500L);
        when(userRoleRepository.findByUserId(7L)).thenReturn(List.of(link(500L, 7L, 10L)));
        when(roleRepository.findByIdInAndDeletedFlagFalse(any()))
                .thenReturn(List.of(role(10L, "A", false)));

        UserRolesResponse response = userRoleService.replaceRoles(7L, List.of(10L));

        verify(userRoleRepository).deleteByUserId(7L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<SysUserRole>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(userRoleRepository).saveAll(captor.capture());
        List<SysUserRole> inserted = new ArrayList<>();
        captor.getValue().forEach(inserted::add);
        assertThat(inserted).extracting(SysUserRole::getRoleId).containsExactly(10L);
        assertThat(response.roles()).extracting(RoleResponse::code).containsExactly("A");
    }

    @Test
    void replaceRoles_shouldAllowClearingAllRoles() {
        when(userAccountRepository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(account(7L)));
        when(userRoleRepository.findByUserId(7L)).thenReturn(List.of());

        UserRolesResponse response = userRoleService.replaceRoles(7L, List.of());

        verify(userRoleRepository).deleteByUserId(7L);
        verify(userRoleRepository, never()).saveAll(any());
        assertThat(response.roles()).isEmpty();
    }

    @Test
    void grantSuperAdmin_shouldAssignWhenRolePresentAndNotAssigned() {
        when(roleRepository.findByCodeAndDeletedFlagFalse(SysRole.SUPER_ADMIN_CODE))
                .thenReturn(Optional.of(role(1L, SysRole.SUPER_ADMIN_CODE, true)));
        when(userRoleRepository.existsByUserIdAndRoleId(7L, 1L)).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(999L);

        userRoleService.grantSuperAdmin(7L);

        verify(userRoleRepository).save(any(SysUserRole.class));
    }

    @Test
    void grantSuperAdmin_shouldBeIdempotentWhenAlreadyAssigned() {
        when(roleRepository.findByCodeAndDeletedFlagFalse(SysRole.SUPER_ADMIN_CODE))
                .thenReturn(Optional.of(role(1L, SysRole.SUPER_ADMIN_CODE, true)));
        when(userRoleRepository.existsByUserIdAndRoleId(7L, 1L)).thenReturn(true);

        userRoleService.grantSuperAdmin(7L);

        verify(userRoleRepository, never()).save(any());
    }

    private UserAccount account(Long id) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setStatus(UserStatus.NORMAL);
        return account;
    }

    private SysRole role(Long id, String code, boolean builtin) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setName(code);
        role.setBuiltin(builtin);
        role.setStatus(StatusConstants.NORMAL);
        return role;
    }

    private SysUserRole link(Long id, Long userId, Long roleId) {
        SysUserRole link = new SysUserRole();
        link.setId(id);
        link.setUserId(userId);
        link.setRoleId(roleId);
        return link;
    }
}
