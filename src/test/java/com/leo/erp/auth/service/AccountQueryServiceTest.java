package com.leo.erp.auth.service;

import com.leo.erp.auth.api.AccountQuery.AccountSnapshot;
import com.leo.erp.auth.api.AuthenticationAccountQuery.AuthenticatedAccountSnapshot;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.repository.UserAccountRepository.CredentialVersionProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AccountQueryService 极端情况测试：映射完整性、NORMAL 状态过滤、credentialVersion null 归一化为 0。
 */
@ExtendWith(MockitoExtension.class)
class AccountQueryServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private AccountQueryService service;

    private UserAccount account() {
        UserAccount account = new UserAccount();
        account.setId(1L);
        account.setLoginName("zhangsan");
        account.setUserName("张三");
        account.setLastLoginDate(LocalDateTime.of(2026, 8, 10, 10, 30));
        account.setStatus(UserStatus.NORMAL);
        account.setCredentialVersion(7L);
        return account;
    }

    // ---------- findById ----------

    @Test
    void findById_shouldMapSnapshotWhenPresent() {
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(account()));

        assertThat(service.findById(1L)).contains(new AccountSnapshot(
                1L, "zhangsan", "张三", LocalDateTime.of(2026, 8, 10, 10, 30)
        ));
    }

    @Test
    void findById_shouldReturnEmptyWhenMissing() {
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        assertThat(service.findById(1L)).isEmpty();
    }

    // ---------- findActiveById ----------

    @Test
    void findActiveById_shouldMapSnapshotWhenNormalStatus() {
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(account()));

        assertThat(service.findActiveById(1L)).contains(new AuthenticatedAccountSnapshot(
                1L, "zhangsan", 7L
        ));
    }

    @Test
    void findActiveById_shouldReturnEmptyWhenNotNormalStatus() {
        UserAccount disabled = account();
        disabled.setStatus(UserStatus.DISABLED);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(disabled));

        assertThat(service.findActiveById(1L)).isEmpty();
    }

    @Test
    void findActiveById_shouldNormalizeNullCredentialVersionToZero() {
        UserAccount account = account();
        account.setCredentialVersion(null);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(account));

        assertThat(service.findActiveById(1L)).contains(new AuthenticatedAccountSnapshot(
                1L, "zhangsan", 0L
        ));
    }

    // ---------- findActiveCredentialVersion ----------

    @Test
    void findActiveCredentialVersion_shouldReturnVersionWhenPresent() {
        CredentialVersionProjection projection = mock(CredentialVersionProjection.class);
        when(projection.getCredentialVersion()).thenReturn(5L);
        when(userAccountRepository.findCredentialVersion(1L, UserStatus.NORMAL))
                .thenReturn(Optional.of(projection));

        assertThat(service.findActiveCredentialVersion(1L)).contains(5L);
    }

    @Test
    void findActiveCredentialVersion_shouldReturnEmptyWhenMissing() {
        when(userAccountRepository.findCredentialVersion(1L, UserStatus.NORMAL))
                .thenReturn(Optional.empty());

        assertThat(service.findActiveCredentialVersion(1L)).isEmpty();
    }

    // 投影返回 null 时 Optional.map 链短路为 empty（normalizeCredentialVersion 不参与该链），
    // 该分支为真实行为：findActiveCredentialVersion 不会对 null 版本归一化。
    @Test
    void findActiveCredentialVersion_shouldReturnEmptyWhenProjectionVersionNull() {
        CredentialVersionProjection projection = mock(CredentialVersionProjection.class);
        when(projection.getCredentialVersion()).thenReturn(null);
        when(userAccountRepository.findCredentialVersion(1L, UserStatus.NORMAL))
                .thenReturn(Optional.of(projection));

        assertThat(service.findActiveCredentialVersion(1L)).isEmpty();
    }
}
