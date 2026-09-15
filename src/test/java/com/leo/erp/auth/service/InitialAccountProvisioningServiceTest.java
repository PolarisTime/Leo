package com.leo.erp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leo.erp.auth.api.InitialAccountCommand;
import com.leo.erp.auth.api.InitialAccountCreated;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.rbac.service.UserRoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 首次初始化建号语义测试：首启判定只针对“是否存在未删除账号”，不因多用户放开而改变。
 */
@ExtendWith(MockitoExtension.class)
class InitialAccountProvisioningServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private UserRoleService userRoleService;

    @InjectMocks
    private InitialAccountProvisioningService service;

    @Test
    void isConfigured_shouldReflectActiveAccountExistence() {
        when(userAccountRepository.existsByDeletedFlagFalse()).thenReturn(false);
        assertThat(service.isConfigured()).isFalse();

        when(userAccountRepository.existsByDeletedFlagFalse()).thenReturn(true);
        assertThat(service.isConfigured()).isTrue();
    }

    @Test
    void provision_whenConfigured_shouldReject() {
        when(userAccountRepository.existsByDeletedFlagFalse()).thenReturn(true);

        assertThatThrownBy(() -> service.provision(
                new InitialAccountCommand("alice", "Init@123", "爱丽丝", "13800000000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已完成初始化");

        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void provision_shouldCreateEncodedAccountAndGrantSuperAdmin() {
        when(userAccountRepository.existsByDeletedFlagFalse()).thenReturn(false);
        when(userAccountRepository.existsByLoginNameAndDeletedFlagFalse("alice")).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(500L);
        when(passwordEncoder.encode("Init@123")).thenReturn("encoded:Init@123");

        InitialAccountCreated created = service.provision(
                new InitialAccountCommand("alice", "Init@123", "爱丽丝", "13800000000"));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded:Init@123");
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.NORMAL);
        assertThat(created.id()).isEqualTo(500L);
        verify(userRoleService).grantSuperAdmin(500L);
    }

    @Test
    void provision_shouldRejectDuplicateLoginName() {
        when(userAccountRepository.existsByDeletedFlagFalse()).thenReturn(false);
        when(userAccountRepository.existsByLoginNameAndDeletedFlagFalse("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.provision(
                new InitialAccountCommand("alice", "Init@123", "爱丽丝", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录账号已存在");

        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void provision_shouldRejectShortPassword() {
        when(userAccountRepository.existsByDeletedFlagFalse()).thenReturn(false);

        assertThatThrownBy(() -> service.provision(
                new InitialAccountCommand("alice", "short", "爱丽丝", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码至少8位");
    }
}
