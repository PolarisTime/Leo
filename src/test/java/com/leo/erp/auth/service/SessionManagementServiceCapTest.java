package com.leo.erp.auth.service;

import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.RevokeReason;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.jwt.AccessTokenBlacklistService;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.jwt.SessionActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话上限行为测试（2026-09-30 压测报告 P1 项的回归保护）。
 *
 * <p>覆盖：上限可配置、上限 &lt;=0 表示不限制、白名单豁免（精确与前缀）、
 * 以及吊销必须留痕（原先完全静默，导致「被顶掉」与「主动登出」无法区分）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionManagementServiceCapTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private RefreshTokenSessionRepository refreshTokenSessionRepository;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private AccessTokenBlacklistService blacklistService;

    @Mock
    private SessionActivityService sessionActivityService;

    @Mock
    private AfterCommitExecutor afterCommitExecutor;

    @Mock
    private SessionEvictionReporter sessionEvictionReporter;

    private AuthProperties authProperties;

    private SessionManagementService service;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        service = new SessionManagementService(
                userAccountRepository,
                refreshTokenSessionRepository,
                jwtTokenService,
                snowflakeIdGenerator,
                blacklistService,
                sessionActivityService,
                afterCommitExecutor,
                authProperties,
                sessionEvictionReporter
        );
        when(snowflakeIdGenerator.nextId()).thenReturn(9001L);
        when(jwtTokenService.getRefreshExpirationMs()).thenReturn(600_000L);
    }

    private UserAccount account(long id, String loginName) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setLoginName(loginName);
        account.setCredentialVersion(0L);
        return account;
    }

    private List<RefreshTokenSession> sessions(int count) {
        List<RefreshTokenSession> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RefreshTokenSession session = new RefreshTokenSession();
            session.setTokenId("session-" + i);
            session.setUserId(7L);
            session.setTokenHash("hash-" + i);
            session.setExpiresAt(LocalDateTime.now().plusMinutes(5));
            list.add(session);
        }
        return list;
    }

    private List<RefreshTokenSession> activeSessions;

    private void givenActiveSessions(int count, String loginName) {
        activeSessions = sessions(count);
        // 登录会话创建刻意**不用**悲观锁读账号行（锁里跑 bcrypt 会打满连接池），
        // 因此这里打桩的是普通读取。
        when(userAccountRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(account(7L, loginName)));
        when(refreshTokenSessionRepository
                .findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(any(), any()))
                .thenReturn(activeSessions);
    }

    /**
     * 只保存了「新会话」这一条：不能拿 save 的调用次数直接判断有没有吊销，
     * 因为新建会话也会 save。真正要看的是既有会话是否被打上吊销标记。
     */
    private void assertNoSessionRevoked() {
        verify(refreshTokenSessionRepository, times(1)).save(any(RefreshTokenSession.class));
        assertThat(activeSessions).allSatisfy(session -> {
            assertThat(session.getRevokedAt()).isNull();
            assertThat(session.getRevokeReason()).isNull();
        });
    }

    @Test
    void maxSessionsFromConfiguration_isApplied() {
        authProperties.getSession().setMaxRefreshTokens(2);
        givenActiveSessions(2, "perf_user");

        service.createSession(7L, "new-session", "raw-token", "127.0.0.1", "k6");

        // 上限 2：发新会话前只允许保留 1 个，因此最旧的 1 个被吊销；save 共 2 次（吊销 + 新会话）
        ArgumentCaptor<RefreshTokenSession> saved = ArgumentCaptor.forClass(RefreshTokenSession.class);
        verify(refreshTokenSessionRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .anySatisfy(session -> {
                    assertThat(session.getTokenId()).isEqualTo("session-0");
                    assertThat(session.getRevokeReason()).isEqualTo(RevokeReason.CONCURRENT_LIMIT);
                });
        verify(sessionEvictionReporter).reportConcurrentLimitRevocation(7L, "perf_user", 1, 2);
    }

    @Test
    void nonPositiveMaxSessions_meansUnlimited() {
        authProperties.getSession().setMaxRefreshTokens(0);
        givenActiveSessions(9, "perf_user");

        service.createSession(7L, "new-session", "raw-token", "127.0.0.1", "k6");

        // 仍然会保存新会话，但不得吊销任何既有会话
        assertNoSessionRevoked();
        verify(sessionEvictionReporter, never()).reportConcurrentLimitRevocation(anyLong(), anyString(), any(Integer.class), any(Integer.class));
    }

    @Test
    void exemptLoginName_bypassesCapEntirely() {
        authProperties.getSession().setMaxRefreshTokens(1);
        authProperties.getSession().setExemptLoginNames(List.of("svc_*", "admin_prod"));
        givenActiveSessions(9, "svc_monitor");

        service.createSession(7L, "new-session", "raw-token", "127.0.0.1", "k6");

        assertNoSessionRevoked();
        verify(sessionEvictionReporter).reportExempt("svc_monitor", 9);
        verify(sessionEvictionReporter, never()).reportConcurrentLimitRevocation(anyLong(), anyString(), any(Integer.class), any(Integer.class));
    }

    @Test
    void exactExemptLoginName_isHonoured() {
        authProperties.getSession().setExemptLoginNames(List.of("admin_prod"));
        givenActiveSessions(5, "admin_prod");

        service.createSession(7L, "new-session", "raw-token", "127.0.0.1", "k6");

        assertNoSessionRevoked();
        verify(sessionEvictionReporter).reportExempt("admin_prod", 5);
    }

    @Test
    void withinCap_doesNotRevokeAnything() {
        authProperties.getSession().setMaxRefreshTokens(3);
        givenActiveSessions(2, "perf_user");

        service.createSession(7L, "new-session", "raw-token", "127.0.0.1", "k6");

        assertNoSessionRevoked();
        verify(sessionEvictionReporter, never()).reportConcurrentLimitRevocation(anyLong(), anyString(), any(Integer.class), any(Integer.class));
    }

    @Test
    void exceedingCap_revokesExactlyTheExcess() {
        authProperties.getSession().setMaxRefreshTokens(3);
        givenActiveSessions(6, "perf_user");

        service.createSession(7L, "new-session", "raw-token", "127.0.0.1", "k6");

        // 上限 3：留 2 个，其余 4 个全部吊销并留痕；save 共 5 次 = 4 次吊销 + 1 次新会话
        verify(refreshTokenSessionRepository, times(5)).save(any(RefreshTokenSession.class));
        assertThat(activeSessions.stream().filter(session -> session.getRevokedAt() != null)).hasSize(4);
        verify(sessionEvictionReporter).reportConcurrentLimitRevocation(7L, "perf_user", 4, 3);
    }
}
