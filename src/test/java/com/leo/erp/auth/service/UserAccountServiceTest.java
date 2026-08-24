package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private UserAccountRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionManagementService sessionManagementService;

    @Mock
    private AuthenticatedUserCacheService authenticatedUserCacheService;

    @Mock
    private UserAccountStatusApplyService statusApplyService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserAccountService service;

    @Test
    void updateStatus_disablingAccount_evictsSnapshotAndRevokesSessions() {
        UserAccount account = new UserAccount();
        account.setId(42L);
        account.setStatus(UserStatus.NORMAL);
        org.mockito.Mockito.when(repository.findByIdAndDeletedFlagFalseForUpdate(42L))
                .thenReturn(java.util.Optional.of(account));
        org.mockito.Mockito.when(repository.saveAndFlush(account)).thenReturn(account);
        doAnswer(invocation -> {
            account.setStatus(invocation.getArgument(1));
            return null;
        }).when(statusApplyService).apply(account, UserStatus.DISABLED);

        service.updateStatus(42L, UserStatus.DISABLED);

        assertThat(account.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(repository).saveAndFlush(account);
        verify(sessionManagementService).revokeActiveSessionsForAccountStatusChange(42L);
        verify(authenticatedUserCacheService).evict(42L);
    }
}
