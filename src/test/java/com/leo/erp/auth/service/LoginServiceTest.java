package com.leo.erp.auth.service;

import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.AuthUserResponse;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.system.operationlog.service.OperationLogCommand;
import com.leo.erp.system.operationlog.service.OperationLogService;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.permission.AuthorityProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 登录链路回归测试（P1 + P2，2026-09-30 审计报告发现 1、2）。
 *
 * <p>P1：登录成功路径对 {@code sys_user} 不得出现 JPA save/saveAndFlush（整行 version 写入口），
 * 唯一写入来自 {@link LoginAuditService} 的原子 UPDATE——本测试用<b>真实</b> issueTokens 执行，
 * 保证守护覆盖到发令牌内部。</p>
 *
 * <p>P2：{@code recordSuccessfulLogin} 必须在 issueTokens 与操作日志<b>之后</b>调用，
 * 把 sys_user 行锁窗口缩到「审计 + 提交」；发令牌失败则不再记录成功登录。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginServiceTest {

    private static final String LOGIN_IP = "127.0.0.1";
    private static final String USER_AGENT = "junit-agent";

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private SessionManagementService sessionManagementService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AuthorityProvider authorityProvider;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = new UserAccount();
        user.setId(11L);
        user.setLoginName("alice");
        user.setUserName("Alice");
        user.setPasswordHash("bcrypt-hash");
        user.setStatus(UserStatus.NORMAL);
        user.setVersion(7L);

        when(userAccountRepository.findByLoginNameAndDeletedFlagFalse("alice"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "bcrypt-hash")).thenReturn(true);
    }

    private LoginRequest request() {
        return new LoginRequest("alice", "secret");
    }

    private LoginService.AuthRequestContext ctx() {
        return new LoginService.AuthRequestContext(LOGIN_IP, USER_AGENT, "/api/v2.0/auth/login", "POST");
    }

    private TokenResponse tokenResponse() {
        return new TokenResponse("access-token", "raw-refresh-token", "Bearer",
                600, 604_800, new AuthUserResponse(11L, "alice", "Alice", List.of()));
    }

    private TokenIssuanceService realTokenIssuanceService() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setId(9001L);
        session.setUserId(11L);
        session.setTokenId("session-001");
        session.setCredentialVersion(0L);
        session.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(sessionManagementService.newSessionTokenId()).thenReturn("session-001");
        when(sessionManagementService.generateRefreshToken()).thenReturn("raw-refresh-token");
        when(sessionManagementService.createSession(
                eq(11L), eq("session-001"), eq("raw-refresh-token"), eq(LOGIN_IP), eq(USER_AGENT)))
                .thenReturn(session);
        when(jwtTokenService.generateAccessToken(any(), eq("session-001"))).thenReturn("access-token");
        when(jwtTokenService.getAccessExpirationMs()).thenReturn(600_000L);
        when(jwtTokenService.getRefreshExpirationMs()).thenReturn(604_800_000L);
        when(authorityProvider.authoritiesFor(any())).thenReturn(List.of("sys:user:read"));

        return new TokenIssuanceService(
                jwtTokenService, sessionManagementService, eventPublisher, authorityProvider);
    }

    /** P2 顺序守护：审计 UPDATE（取得 sys_user 行锁）必须在发令牌与操作日志之后、最末执行。 */
    @Test
    void login_recordsSuccessfulLoginAfterIssuanceAndOperationLog() {
        TokenIssuanceService tokens = mock(TokenIssuanceService.class);
        LoginAuditService audit = mock(LoginAuditService.class);
        when(tokens.issueTokens(user, LOGIN_IP, USER_AGENT)).thenReturn(tokenResponse());
        LoginService service = new LoginService(
                userAccountRepository, passwordEncoder, loginAttemptService,
                tokens, operationLogService, audit);

        service.login(request(), ctx());

        InOrder order = inOrder(loginAttemptService, tokens, operationLogService, audit);
        order.verify(loginAttemptService).ensureLoginAllowed("alice");
        order.verify(loginAttemptService).clearFailures("alice");
        order.verify(tokens).issueTokens(user, LOGIN_IP, USER_AGENT);
        order.verify(operationLogService).record(any(OperationLogCommand.class));
        order.verify(audit).recordSuccessfulLogin(user);
    }

    /** P1 守护：issueTokens 真实执行全程不 save；对 sys_user 的写入仅来自 LoginAuditService。 */
    @Test
    void login_successWritesSysUserOnlyThroughLoginAuditService() {
        LoginAuditService audit = spy(new LoginAuditService(userAccountRepository, new AuthProperties()));
        when(userAccountRepository.updateLastLoginDate(anyLong(), any(), any())).thenReturn(1);
        LoginService service = new LoginService(
                userAccountRepository, passwordEncoder, loginAttemptService,
                realTokenIssuanceService(), operationLogService, audit);

        TokenResponse response = service.login(request(), ctx());

        assertThat(response.accessToken()).isEqualTo("access-token");
        // 写入归因：唯一的写交互是审计原子 UPDATE，且确由 LoginAuditService 发出
        verify(audit).recordSuccessfulLogin(user);
        verify(userAccountRepository).updateLastLoginDate(eq(11L), any(LocalDateTime.class),
                any(LocalDateTime.class));
        // P1 硬守护：登录链路任何位置都不允许 JPA save/saveAndFlush 触碰 UserAccount
        verify(userAccountRepository, never()).save(any(UserAccount.class));
        verify(userAccountRepository, never()).saveAndFlush(any(UserAccount.class));
        // 读（按登录名查）+ 审计原子 UPDATE 之外，对 sys_user 不得有任何其它交互
        verify(userAccountRepository).findByLoginNameAndDeletedFlagFalse("alice");
        verifyNoMoreInteractions(userAccountRepository);
    }

    /** P2 语义：发令牌失败 → 本次登录失败，不记录成功登录（审计 UPDATE 也不再发出）。 */
    @Test
    void login_issuanceFailure_skipsSuccessfulLoginAudit() {
        TokenIssuanceService tokens = mock(TokenIssuanceService.class);
        LoginAuditService audit = mock(LoginAuditService.class);
        when(tokens.issueTokens(user, LOGIN_IP, USER_AGENT))
                .thenThrow(new IllegalStateException("session write failed"));
        LoginService service = new LoginService(
                userAccountRepository, passwordEncoder, loginAttemptService,
                tokens, operationLogService, audit);

        assertThatThrownBy(() -> service.login(request(), ctx()))
                .isInstanceOf(IllegalStateException.class);
        verify(audit, never()).recordSuccessfulLogin(any());
        verify(operationLogService, never()).record(any(OperationLogCommand.class));
    }

    /** P2 语义边界：密码错误时不发令牌、不记录成功登录（既有失败路径不受顺序调整影响）。 */
    @Test
    void login_badPassword_neitherIssuesTokensNorRecordsAudit() {
        when(passwordEncoder.matches("secret", "bcrypt-hash")).thenReturn(false);
        TokenIssuanceService tokens = mock(TokenIssuanceService.class);
        LoginAuditService audit = mock(LoginAuditService.class);
        LoginService service = new LoginService(
                userAccountRepository, passwordEncoder, loginAttemptService,
                tokens, operationLogService, audit);

        assertThatThrownBy(() -> service.login(request(), ctx()))
                .isInstanceOf(BadCredentialsException.class);
        verify(tokens, never()).issueTokens(any(), any(), any());
        verify(audit, never()).recordSuccessfulLogin(any());
        verify(userAccountRepository, never()).updateLastLoginDate(anyLong(), any(), any());
    }
}
