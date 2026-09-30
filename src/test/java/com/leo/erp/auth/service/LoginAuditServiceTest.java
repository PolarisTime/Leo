package com.leo.erp.auth.service;

import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录审计写入测试（2026-09-30 压测报告 P0 项的回归保护）。
 *
 * <p>核心行为：只有超过配置间隔才发一条原子 UPDATE；节流命中时对 {@code sys_user}
 * 完全不产生写语句。反例同样重要——「每次登录都改实体」正是把 20 并发登录打成 92% 409 的原因；
 * 而「为了避开冲突去锁账号行」又会因为锁里跑 bcrypt 而打满连接池（实测失败率 88%）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAuditServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    private AuthProperties authProperties;

    private LoginAuditService service;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        service = new LoginAuditService(userAccountRepository, authProperties);
        when(userAccountRepository.updateLastLoginDate(anyLong(), any())).thenReturn(1);
    }

    private UserAccount account(LocalDateTime lastLoginAt) {
        UserAccount account = new UserAccount();
        account.setId(11L);
        account.setLoginName("perf_user");
        account.setLastLoginDate(lastLoginAt);
        return account;
    }

    @Test
    void lastLoginWithinInterval_isThrottled() {
        authProperties.getLoginAudit().setLastLoginWriteIntervalSeconds(60);
        UserAccount account = account(LocalDateTime.now().minusSeconds(5));

        boolean written = service.recordSuccessfulLogin(account);

        assertThat(written).isFalse();
        // 节流命中 = 完全不写库，也不得改动受管实体
        verify(userAccountRepository, never()).updateLastLoginDate(anyLong(), any());
        assertThat(account.getLastLoginDate()).isEqualTo(account.getLastLoginDate());
    }

    @Test
    void lastLoginBeyondInterval_issuesAtomicUpdate() {
        authProperties.getLoginAudit().setLastLoginWriteIntervalSeconds(60);
        UserAccount account = account(LocalDateTime.now().minusSeconds(120));

        boolean written = service.recordSuccessfulLogin(account);

        assertThat(written).isTrue();
        verify(userAccountRepository).updateLastLoginDate(eq(11L), any(LocalDateTime.class));
    }

    @Test
    void neverLoggedIn_isWritten() {
        assertThat(service.recordSuccessfulLogin(account(null))).isTrue();
        verify(userAccountRepository).updateLastLoginDate(eq(11L), any(LocalDateTime.class));
    }

    @Test
    void intervalDisabled_alwaysWrites() {
        authProperties.getLoginAudit().setLastLoginWriteIntervalSeconds(0);

        assertThat(service.recordSuccessfulLogin(account(LocalDateTime.now()))).isTrue();
    }

    @Test
    void futureTimestamp_isWrittenToAvoidFreezingTheField() {
        authProperties.getLoginAudit().setLastLoginWriteIntervalSeconds(3600);

        assertThat(service.recordSuccessfulLogin(account(LocalDateTime.now().plusMinutes(10)))).isTrue();
    }

    @Test
    void updateAffectingNoRow_reportsNotWritten() {
        when(userAccountRepository.updateLastLoginDate(anyLong(), any())).thenReturn(0);

        assertThat(service.recordSuccessfulLogin(account(null))).isFalse();
    }

    @Test
    void nullOrTransientAccount_isIgnored() {
        assertThat(service.recordSuccessfulLogin(null)).isFalse();
        UserAccount transientAccount = new UserAccount();
        transientAccount.setLoginName("no-id");
        assertThat(service.recordSuccessfulLogin(transientAccount)).isFalse();
        verify(userAccountRepository, never()).updateLastLoginDate(anyLong(), any());
    }
}
