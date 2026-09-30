package com.leo.erp.auth.service;

import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
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
 *
 * <p>P2 后节流是两道闸：本测试覆盖第一道闸（快照预判：60s 内不发 UPDATE、60s 外发）与
 * 第二道闸的入参（staleBefore = now - interval，随 UPDATE 进 WHERE）；WHERE 条件本身的形状
 * 由 {@code UserAccountRepositoryQueryTest} 守护。</p>
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
        when(userAccountRepository.updateLastLoginDate(anyLong(), any(), any())).thenReturn(1);
    }

    private UserAccount account(LocalDateTime lastLoginAt) {
        UserAccount account = new UserAccount();
        account.setId(11L);
        account.setLoginName("perf_user");
        account.setLastLoginDate(lastLoginAt);
        return account;
    }

    private LocalDateTime captureStaleBefore() {
        ArgumentCaptor<LocalDateTime> staleBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userAccountRepository).updateLastLoginDate(
                eq(11L), any(LocalDateTime.class), staleBefore.capture());
        return staleBefore.getValue();
    }

    @Test
    void lastLoginWithinInterval_isThrottled() {
        authProperties.getLoginAudit().setLastLoginWriteIntervalSeconds(60);
        UserAccount account = account(LocalDateTime.now().minusSeconds(5));

        boolean written = service.recordSuccessfulLogin(account);

        assertThat(written).isFalse();
        // 节流命中 = 完全不写库，也不得改动受管实体，更不许走 JPA save（整行 version 写入口）
        verify(userAccountRepository, never()).updateLastLoginDate(anyLong(), any(), any());
        verify(userAccountRepository, never()).save(any(UserAccount.class));
        verify(userAccountRepository, never()).saveAndFlush(any(UserAccount.class));
    }

    @Test
    void lastLoginBeyondInterval_issuesAtomicUpdate_withStaleBeforeInWhere() {
        authProperties.getLoginAudit().setLastLoginWriteIntervalSeconds(60);
        UserAccount account = account(LocalDateTime.now().minusSeconds(120));

        boolean written = service.recordSuccessfulLogin(account);

        assertThat(written).isTrue();
        // 第二道闸入参：staleBefore = now - 60s，随 UPDATE 进入 WHERE 的节流条件
        LocalDateTime staleBefore = captureStaleBefore();
        long driftSeconds = Math.abs(
                Duration.between(staleBefore, LocalDateTime.now().minusSeconds(60)).getSeconds());
        assertThat(driftSeconds).isLessThan(10);
    }

    @Test
    void lastLoginBeyondCustomInterval_usesConfiguredIntervalAsStaleBefore() {
        authProperties.getLoginAudit().setLastLoginWriteIntervalSeconds(300);
        UserAccount account = account(LocalDateTime.now().minusSeconds(600));

        assertThat(service.recordSuccessfulLogin(account)).isTrue();

        LocalDateTime staleBefore = captureStaleBefore();
        long driftSeconds = Math.abs(
                Duration.between(staleBefore, LocalDateTime.now().minusSeconds(300)).getSeconds());
        assertThat(driftSeconds).isLessThan(10);
    }

    @Test
    void neverLoggedIn_isWritten() {
        assertThat(service.recordSuccessfulLogin(account(null))).isTrue();
        // lastLoginDate 为空由 WHERE 的 "lastLoginDate IS NULL" 分支放行（条件形状见仓库查询测试）
        verify(userAccountRepository).updateLastLoginDate(eq(11L), any(LocalDateTime.class),
                any(LocalDateTime.class));
    }

    @Test
    void intervalDisabled_alwaysWrites() {
        authProperties.getLoginAudit().setLastLoginWriteIntervalSeconds(0);

        assertThat(service.recordSuccessfulLogin(account(LocalDateTime.now()))).isTrue();

        // 阈值关闭（<=0 = 每次都写）：staleBefore 推到遥远未来，WHERE 节流条件恒为真
        LocalDateTime staleBefore = captureStaleBefore();
        assertThat(staleBefore).isAfter(LocalDateTime.now().plusYears(50));
    }

    @Test
    void futureTimestamp_isWrittenToAvoidFreezingTheField() {
        authProperties.getLoginAudit().setLastLoginWriteIntervalSeconds(3600);

        // 第一道闸对「未来值」（时钟漂移）放行并发出 UPDATE；WHERE 是否落库由数据库按条件裁决
        assertThat(service.recordSuccessfulLogin(account(LocalDateTime.now().plusMinutes(10)))).isTrue();
        verify(userAccountRepository).updateLastLoginDate(eq(11L), any(LocalDateTime.class),
                any(LocalDateTime.class));
    }

    @Test
    void updateAffectingNoRow_reportsNotWritten() {
        when(userAccountRepository.updateLastLoginDate(anyLong(), any(), any())).thenReturn(0);

        // WHERE 重评后条件不成立（并发突发下其余事务的典型结果）→ 返回 false，且不影响调用方
        assertThat(service.recordSuccessfulLogin(account(null))).isFalse();
    }

    @Test
    void nullOrTransientAccount_isIgnored() {
        assertThat(service.recordSuccessfulLogin(null)).isFalse();
        UserAccount transientAccount = new UserAccount();
        transientAccount.setLoginName("no-id");
        assertThat(service.recordSuccessfulLogin(transientAccount)).isFalse();
        verify(userAccountRepository, never()).updateLastLoginDate(anyLong(), any(), any());
    }
}
