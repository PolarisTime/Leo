package com.leo.erp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.UserAccountCreateRequest;
import com.leo.erp.auth.web.dto.UserAccountResponse;
import com.leo.erp.auth.web.dto.UserAccountUpdateRequest;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.rbac.repository.SysRolePermissionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 多用户账号管理服务测试：建号加密与唯一性、停用/删除的锁死保护、删除自己、密码重置。
 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private SysRolePermissionRepository rolePermissionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SessionManagementService sessionManagementService;

    @Mock
    private AuthenticatedUserCacheService authenticatedUserCacheService;

    @Mock
    private UserAccountStatusApplyService statusApplyService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserAdminService service;

    @Test
    void create_shouldEncodePasswordAndPersistWithoutRole() {
        when(userAccountRepository.existsByLoginNameAndDeletedFlagFalse("alice")).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(passwordEncoder.encode("Init@123")).thenReturn("encoded:Init@123");

        UserAccountResponse response = service.create(
                new UserAccountCreateRequest("alice", "爱丽丝", "Init@123", "13800000000", null));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).saveAndFlush(captor.capture());
        UserAccount saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(100L);
        assertThat(saved.getLoginName()).isEqualTo("alice");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded:Init@123");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.NORMAL);
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(UserStatus.NORMAL);
    }

    @Test
    void create_shouldSucceedEvenWhenAccountsAlreadyExist() {
        when(userAccountRepository.existsByLoginNameAndDeletedFlagFalse("bob")).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(101L);
        when(passwordEncoder.encode("Init@123")).thenReturn("encoded");

        service.create(new UserAccountCreateRequest("bob", "鲍勃", "Init@123", null, null));

        verify(userAccountRepository, never()).existsByDeletedFlagFalse();
        verify(userAccountRepository).saveAndFlush(any(UserAccount.class));
    }

    @Test
    void create_shouldRejectDuplicateLoginName() {
        when(userAccountRepository.existsByLoginNameAndDeletedFlagFalse("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new UserAccountCreateRequest("alice", "爱丽丝", "Init@123", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录账号已存在");

        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldRejectBlankLoginName() {
        assertThatThrownBy(() -> service.create(
                new UserAccountCreateRequest("  ", "爱丽丝", "Init@123", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录账号不能为空");
    }

    @Test
    void create_shouldRejectShortPassword() {
        assertThatThrownBy(() -> service.create(
                new UserAccountCreateRequest("alice", "爱丽丝", "short", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码至少8位");

        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateStatus_disablingLastActiveAdmin_shouldReject() {
        UserAccount account = account(42L, UserStatus.NORMAL);
        when(userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(42L))
                .thenReturn(Optional.of(account));
        when(rolePermissionRepository.findPermissionCodesByUserId(42L, StatusConstants.NORMAL))
                .thenReturn(List.of(PermissionCodes.WILDCARD));
        when(userAccountRepository.countActiveAdminsWithPermission(
                PermissionCodes.WILDCARD, StatusConstants.NORMAL)).thenReturn(1L);

        assertThatThrownBy(() -> service.updateStatus(42L, UserStatus.DISABLED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少保留一个");
        assertThat(account.getStatus()).isEqualTo(UserStatus.NORMAL);
        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateStatus_disablingWhenAnotherActiveAdminExists_shouldSucceed() {
        UserAccount account = account(42L, UserStatus.NORMAL);
        when(userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(42L))
                .thenReturn(Optional.of(account));
        when(rolePermissionRepository.findPermissionCodesByUserId(42L, StatusConstants.NORMAL))
                .thenReturn(List.of(PermissionCodes.WILDCARD));
        when(userAccountRepository.countActiveAdminsWithPermission(
                PermissionCodes.WILDCARD, StatusConstants.NORMAL)).thenReturn(2L);
        doAnswer(invocation -> {
            account.setStatus(invocation.getArgument(1));
            return null;
        }).when(statusApplyService).apply(account, UserStatus.DISABLED);

        UserAccountResponse response = service.updateStatus(42L, UserStatus.DISABLED);

        assertThat(response.status()).isEqualTo(UserStatus.DISABLED);
        verify(userAccountRepository).saveAndFlush(account);
        verify(sessionManagementService).revokeActiveSessionsForAccountStatusChange(42L);
        verify(authenticatedUserCacheService).evict(42L);
    }

    @Test
    void updateStatus_enablingAccount_shouldNotCheckAdminContinuity() {
        UserAccount account = account(42L, UserStatus.DISABLED);
        when(userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(42L))
                .thenReturn(Optional.of(account));
        doAnswer(invocation -> {
            account.setStatus(invocation.getArgument(1));
            return null;
        }).when(statusApplyService).apply(account, UserStatus.NORMAL);

        service.updateStatus(42L, UserStatus.NORMAL);

        assertThat(account.getStatus()).isEqualTo(UserStatus.NORMAL);
        verify(userAccountRepository, never()).countActiveAdminsWithPermission(any(), any());
        verify(sessionManagementService, never()).revokeActiveSessionsForAccountStatusChange(anyLong());
    }

    @Test
    void delete_shouldRejectDeletingSelf() {
        assertThatThrownBy(() -> service.delete(42L, 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除当前登录账号");

        verify(userAccountRepository, never()).findByIdAndDeletedFlagFalseForUpdate(anyLong());
    }

    @Test
    void delete_shouldRejectDeletingLastActiveAdmin() {
        UserAccount account = account(42L, UserStatus.NORMAL);
        when(userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(42L))
                .thenReturn(Optional.of(account));
        when(rolePermissionRepository.findPermissionCodesByUserId(42L, StatusConstants.NORMAL))
                .thenReturn(List.of(PermissionCodes.WILDCARD));
        when(userAccountRepository.countActiveAdminsWithPermission(
                PermissionCodes.WILDCARD, StatusConstants.NORMAL)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(42L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少保留一个");
        assertThat(account.isDeletedFlag()).isFalse();
        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void delete_shouldSoftDeleteWhenAnotherActiveAdminExists() {
        UserAccount account = account(42L, UserStatus.NORMAL);
        when(userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(42L))
                .thenReturn(Optional.of(account));
        when(rolePermissionRepository.findPermissionCodesByUserId(42L, StatusConstants.NORMAL))
                .thenReturn(List.of(PermissionCodes.WILDCARD));
        when(userAccountRepository.countActiveAdminsWithPermission(
                PermissionCodes.WILDCARD, StatusConstants.NORMAL)).thenReturn(2L);

        service.delete(42L, 7L);

        assertThat(account.isDeletedFlag()).isTrue();
        assertThat(account.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(userAccountRepository).saveAndFlush(account);
        verify(sessionManagementService).revokeActiveSessionsForAccountStatusChange(42L);
    }

    @Test
    void delete_shouldAllowDeletingNonAdminWithoutContinuityCheck() {
        UserAccount account = account(42L, UserStatus.NORMAL);
        when(userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(42L))
                .thenReturn(Optional.of(account));
        when(rolePermissionRepository.findPermissionCodesByUserId(42L, StatusConstants.NORMAL))
                .thenReturn(List.of(PermissionCodes.USER_ACCOUNTS_READ));

        service.delete(42L, 7L);

        assertThat(account.isDeletedFlag()).isTrue();
        verify(userAccountRepository, never()).countActiveAdminsWithPermission(any(), any());
    }

    @Test
    void resetPassword_shouldEncodeAndBumpCredentialVersion() {
        UserAccount account = account(42L, UserStatus.NORMAL);
        account.setCredentialVersion(3L);
        when(userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(42L))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.encode("NewPass@123")).thenReturn("encoded:new");

        service.resetPassword(42L, "NewPass@123");

        assertThat(account.getPasswordHash()).isEqualTo("encoded:new");
        assertThat(account.getCredentialVersion()).isEqualTo(4L);
        verify(sessionManagementService).revokeActiveSessionsForPasswordChange(42L);
        verify(authenticatedUserCacheService).evict(42L);
    }

    @Test
    void resetPassword_shouldRejectShortPassword() {
        assertThatThrownBy(() -> service.resetPassword(42L, "short"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码至少8位");
        verify(userAccountRepository, never()).findByIdAndDeletedFlagFalseForUpdate(anyLong());
    }

    @Test
    void page_shouldMapEntitiesAndAcceptStatusFilter() {
        UserAccount account = account(42L, UserStatus.DISABLED);
        when(userAccountRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account)));

        var page = service.page(new PageQuery(0, 20, null, null), "alice", "disabled");

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().status()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void page_shouldRejectInvalidStatusFilter() {
        assertThatThrownBy(() -> service.page(new PageQuery(0, 20, null, null), null, "未知"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号状态不合法");
    }

    @Test
    void detail_shouldThrowWhenNotFound() {
        when(userAccountRepository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号不存在");
    }

    @Test
    void update_shouldEditProfileWithoutPassword() {
        UserAccount account = account(42L, UserStatus.NORMAL);
        when(userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(42L))
                .thenReturn(Optional.of(account));

        UserAccountResponse response = service.update(42L,
                new UserAccountUpdateRequest("新名字", "13900000000", null));

        assertThat(response.userName()).isEqualTo("新名字");
        assertThat(response.mobile()).isEqualTo("13900000000");
        verify(userAccountRepository).saveAndFlush(account);
    }

    @Test
    void update_shouldApplyStatusChangeWithContinuityProtection() {
        UserAccount account = account(42L, UserStatus.NORMAL);
        when(userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(42L))
                .thenReturn(Optional.of(account));
        when(rolePermissionRepository.findPermissionCodesByUserId(42L, StatusConstants.NORMAL))
                .thenReturn(List.of(PermissionCodes.WILDCARD));
        when(userAccountRepository.countActiveAdminsWithPermission(
                PermissionCodes.WILDCARD, StatusConstants.NORMAL)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(42L,
                new UserAccountUpdateRequest("系统管理员", null, UserStatus.DISABLED)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少保留一个");
    }

    private UserAccount account(Long id, UserStatus status) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setLoginName("user-" + id);
        account.setUserName("用户" + id);
        account.setStatus(status);
        return account;
    }
}
