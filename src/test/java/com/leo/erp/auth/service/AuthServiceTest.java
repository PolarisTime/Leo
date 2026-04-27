package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.LoginResponseBody;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.jwt.AccessTokenBlacklistService;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.jwt.SessionActivityService;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.operationlog.service.OperationLogCommand;
import com.leo.erp.system.operationlog.service.OperationLogService;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTest {

    // --- Session revocation (TokenIssuanceService) ---

    @Test
    void shouldRevokeOnlyOldestActiveRefreshTokensWhenLimitExceeded() {
        List<RefreshTokenSession> activeTokens = List.of(
                token(1L), token(2L), token(3L), token(4L), token(5L)
        );
        List<RefreshTokenSession> savedTokens = new ArrayList<>();
        List<String> blacklistedSessionIds = new ArrayList<>();
        AtomicReference<Long> queriedUserId = new AtomicReference<>();
        AtomicReference<LocalDateTime> queriedNow = new AtomicReference<>();
        RefreshTokenSessionRepository repository = refreshTokenSessionRepository(activeTokens, savedTokens, queriedUserId, queriedNow);

        TokenIssuanceService service = new TokenIssuanceService(
                null, repository, jwtTokenService(), new SnowflakeIdGenerator(0L),
                blacklistService(blacklistedSessionIds, new AtomicBoolean(false)),
                sessionActivityService(), permissionService(), userRoleBindingService(),
                afterCommitExecutor(), null
        );

        // trimActiveSessionsBeforeIssuing is private; verify through refresh flow
        // that it limits sessions. Use ReflectionTestUtils to invoke directly.
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "trimActiveSessionsBeforeIssuing", 42L);

        assertThat(queriedUserId.get()).isEqualTo(42L);
        assertThat(queriedNow.get()).isNotNull();
        // trimActiveSessionsBeforeIssuing keeps MAX-1=2, so 3 of 5 are revoked
        assertThat(savedTokens).hasSize(3);
        assertThat(blacklistedSessionIds).hasSize(3);
    }

    @Test
    void shouldBlacklistOnlyCurrentSessionWhenLogout() {
        RefreshTokenSession session = token(11L);
        session.setTokenHash(TokenIssuanceService.hashToken("refresh-token"));
        List<String> blacklistedSessionIds = new ArrayList<>();
        AtomicBoolean blacklistedUser = new AtomicBoolean(false);
        List<RefreshTokenSession> savedTokens = new ArrayList<>();
        RefreshTokenSessionRepository repository = logoutRefreshTokenSessionRepository(session, savedTokens);

        TokenIssuanceService tokenService = new TokenIssuanceService(
                null, repository, jwtTokenService(), new SnowflakeIdGenerator(0L),
                blacklistService(blacklistedSessionIds, blacklistedUser),
                sessionActivityService(), permissionService(), userRoleBindingService(),
                afterCommitExecutor(), null
        );

        tokenService.logout("refresh-token");

        assertThat(savedTokens).containsExactly(session);
        assertThat(session.getRevokedAt()).isNotNull();
        assertThat(blacklistedSessionIds).containsExactly(session.getTokenId());
        assertThat(blacklistedUser.get()).isFalse();
    }

    // --- Login flow (LoginService) ---

    @Test
    void shouldRecordOperationLogWhenLoginSucceedsWithoutTotp() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setLoginName("tester");
        user.setUserName("测试用户");
        user.setPasswordHash("encoded:secret");
        user.setStatus(com.leo.erp.auth.domain.enums.UserStatus.NORMAL);
        user.setTotpEnabled(Boolean.FALSE);
        user.setRequireTotpSetup(Boolean.FALSE);

        List<OperationLogCommand> loggedCommands = new ArrayList<>();

        LoginService loginService = buildLoginService(user, null, loggedCommands);

        LoginResponseBody response = loginService.login(
                new LoginRequest("tester", "secret"),
                "127.0.0.1", "JUnit", "/auth/login", "POST"
        );

        assertThat(response).isInstanceOf(com.leo.erp.auth.web.dto.TokenResponse.class);
        assertThat(loggedCommands).hasSize(1);
        OperationLogCommand command = loggedCommands.get(0);
        assertThat(command.moduleName()).isEqualTo("认证授权");
        assertThat(command.actionType()).isEqualTo("登录");
        assertThat(command.businessNo()).isEqualTo("tester");
        assertThat(command.requestPath()).isEqualTo("/auth/login");
        assertThat(command.requestMethod()).isEqualTo("POST");
        assertThat(command.clientIp()).isEqualTo("127.0.0.1");
        assertThat(command.resultStatus()).isEqualTo("成功");
        assertThat(command.operatorId()).isEqualTo(7L);
        assertThat(command.operatorName()).isEqualTo("测试用户");
        assertThat(command.loginName()).isEqualTo("tester");
    }

    @Test
    void shouldRecordOperationLogWhenLoginFails() {
        List<OperationLogCommand> loggedCommands = new ArrayList<>();

        LoginService loginService = buildLoginService(null, null, loggedCommands);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> loginService.login(
                new LoginRequest("tester", "secret"),
                "127.0.0.1", "JUnit", "/auth/login", "POST"
        )).isInstanceOf(BadCredentialsException.class);

        assertThat(loggedCommands).hasSize(1);
        OperationLogCommand command = loggedCommands.get(0);
        assertThat(command.actionType()).isEqualTo("登录失败");
        assertThat(command.businessNo()).isEqualTo("tester");
        assertThat(command.resultStatus()).isEqualTo("失败");
        assertThat(command.loginName()).isEqualTo("tester");
        assertThat(command.operatorId()).isNull();
    }

    // --- AuthService facade (logout with logging) ---

    @Test
    void shouldRecordOperationLogWhenLogoutSucceeds() {
        UserAccount user = new UserAccount();
        user.setId(42L);
        user.setLoginName("tester");
        user.setUserName("测试用户");

        RefreshTokenSession session = token(11L);
        session.setTokenHash(TokenIssuanceService.hashToken("refresh-token"));
        List<RefreshTokenSession> savedTokens = new ArrayList<>();
        List<OperationLogCommand> loggedCommands = new ArrayList<>();

        TokenIssuanceService tokenService = new TokenIssuanceService(
                logoutUserAccountRepository(user),
                logoutRefreshTokenSessionRepository(session, savedTokens),
                jwtTokenService(), new SnowflakeIdGenerator(0L),
                blacklistService(new ArrayList<>(), new AtomicBoolean(false)),
                sessionActivityService(), permissionService(), userRoleBindingService(),
                afterCommitExecutor(), null
        );

        LoginService loginService = buildLoginService(user, null, loggedCommands);

        AuthService authService = new AuthService(
                loginService, tokenService,
                operationLogService(loggedCommands),
                systemSwitchService(true)
        );

        authService.logout("refresh-token", "127.0.0.1", "/auth/logout", "POST");

        assertThat(loggedCommands).hasSize(1);
        OperationLogCommand command = loggedCommands.get(0);
        assertThat(command.actionType()).isEqualTo("退出登录");
        assertThat(command.businessNo()).isEqualTo("tester");
        assertThat(command.resultStatus()).isEqualTo("成功");
        assertThat(command.operatorId()).isEqualTo(42L);
        assertThat(command.operatorName()).isEqualTo("测试用户");
    }

    // --- Helpers ---

    private LoginService buildLoginService(UserAccount user, StringRedisTemplate redisTemplate, List<OperationLogCommand> loggedCommands) {
        UserAccountRepository userRepo = loginUserAccountRepository(user);
        PasswordEncoder encoder = passwordEncoder();
        TotpService totpService = totpService();
        LoginAttemptService loginAttempt = loginAttemptService();
        TokenIssuanceService tokenIssuance = new TokenIssuanceService(
                userRepo,
                loginRefreshTokenSessionRepository(),
                jwtTokenService(), new SnowflakeIdGenerator(0L),
                blacklistService(new ArrayList<>(), new AtomicBoolean(false)),
                sessionActivityService(), permissionService(), userRoleBindingService(),
                afterCommitExecutor(), null
        );
        return new LoginService(
                userRepo, encoder, totpService, loginAttempt,
                redisTemplate != null ? redisTemplate : stringRedisTemplate(),
                tokenIssuance,
                operationLogService(loggedCommands),
                systemSwitchService(true)
        );
    }

    private RefreshTokenSessionRepository refreshTokenSessionRepository(List<RefreshTokenSession> activeTokens,
                                                                       List<RefreshTokenSession> savedTokens,
                                                                       AtomicReference<Long> queriedUserId,
                                                                       AtomicReference<LocalDateTime> queriedNow) {
        return (RefreshTokenSessionRepository) Proxy.newProxyInstance(
                RefreshTokenSessionRepository.class.getClassLoader(),
                new Class[]{RefreshTokenSessionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc" -> {
                        queriedUserId.set((Long) args[0]);
                        queriedNow.set((LocalDateTime) args[1]);
                        yield activeTokens;
                    }
                    case "save" -> {
                        RefreshTokenSession entity = (RefreshTokenSession) args[0];
                        savedTokens.add(entity);
                        yield entity;
                    }
                    case "toString" -> "RefreshTokenSessionRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RefreshTokenSessionRepository logoutRefreshTokenSessionRepository(RefreshTokenSession session,
                                                                             List<RefreshTokenSession> savedTokens) {
        return (RefreshTokenSessionRepository) Proxy.newProxyInstance(
                RefreshTokenSessionRepository.class.getClassLoader(),
                new Class[]{RefreshTokenSessionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByTokenHashAndDeletedFlagFalse" -> Optional.of(session);
                    case "save" -> {
                        RefreshTokenSession entity = (RefreshTokenSession) args[0];
                        savedTokens.add(entity);
                        yield entity;
                    }
                    case "toString" -> "RefreshTokenSessionRepositoryLogoutStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountRepository loginUserAccountRepository(UserAccount user) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByLoginNameAndDeletedFlagFalse" -> Optional.ofNullable(user);
                    case "save" -> args[0];
                    case "toString" -> "UserAccountRepositoryLoginStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountRepository logoutUserAccountRepository(UserAccount user) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.ofNullable(user);
                    case "toString" -> "UserAccountRepositoryLogoutUserStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RefreshTokenSessionRepository loginRefreshTokenSessionRepository() {
        return (RefreshTokenSessionRepository) Proxy.newProxyInstance(
                RefreshTokenSessionRepository.class.getClassLoader(),
                new Class[]{RefreshTokenSessionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc" -> List.of();
                    case "save" -> args[0];
                    case "toString" -> "RefreshTokenSessionRepositoryLoginStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private AccessTokenBlacklistService blacklistService(List<String> blacklistedSessionIds, AtomicBoolean blacklistedUser) {
        return new AccessTokenBlacklistService(null, null) {
            @Override
            public void blacklistSession(String sessionId) {
                blacklistedSessionIds.add(sessionId);
            }
            @Override
            public void blacklistUser(Long userId) {
                blacklistedUser.set(true);
            }
        };
    }

    private SessionActivityService sessionActivityService() {
        return new SessionActivityService(null) {
            @Override
            public void clearSession(String sessionId) {}
            @Override
            public void touchSession(String sessionId) {}
        };
    }

    private LoginAttemptService loginAttemptService() {
        return new LoginAttemptService(null, new com.leo.erp.auth.config.AuthProperties()) {
            @Override
            public void ensureLoginAllowed(String loginName) {}
            @Override
            public void recordFailure(String loginName) {}
            @Override
            public void clearFailures(String loginName) {}
        };
    }

    private TotpService totpService() {
        return new TotpService(
                new com.leo.erp.security.totp.TotpProperties("test", null),
                null, null
        ) {
            @Override
            public String decryptSecret(String encrypted) { return "secret"; }
            @Override
            public boolean verifyCode(String secret, String code) { return true; }
        };
    }

    private PasswordEncoder passwordEncoder() {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) { return "encoded:" + rawPassword; }
            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return ("encoded:" + rawPassword).equals(encodedPassword);
            }
        };
    }

    private JwtTokenService jwtTokenService() {
        return new JwtTokenService(null, null) {
            @Override
            public String generateAccessToken(com.leo.erp.security.support.SecurityPrincipal principal, String sessionId) {
                return "access-token";
            }
            @Override
            public long getAccessExpirationMs() { return 300_000L; }
            @Override
            public long getRefreshExpirationMs() { return 1_800_000L; }
        };
    }

    private PermissionService permissionService() {
        return new PermissionService(null, null, null, null, null, null) {
            @Override
            public void evictCache(Long userId) {}
            @Override
            public List<com.leo.erp.auth.web.dto.ResourcePermissionResponse> getUserPermissions(Long userId) {
                return List.of(new com.leo.erp.auth.web.dto.ResourcePermissionResponse("user-account", java.util.Set.of("read")));
            }
            @Override
            public Map<String, String> getUserDataScopes(Long userId) {
                return Map.of("user-account", "all");
            }
        };
    }

    private UserRoleBindingService userRoleBindingService() {
        return new UserRoleBindingService(null, null, null) {
            @Override
            public List<RoleSetting> resolveRolesForUser(Long userId) {
                RoleSetting role = new RoleSetting();
                role.setId(1L);
                role.setRoleCode("ADMIN");
                role.setRoleName("管理员");
                role.setStatus("正常");
                return List.of(role);
            }
        };
    }

    private OperationLogService operationLogService(List<OperationLogCommand> loggedCommands) {
        return new OperationLogService(null, null, null, null) {
            @Override
            public void record(OperationLogCommand command) { loggedCommands.add(command); }
        };
    }

    private SystemSwitchService systemSwitchService(boolean enabled) {
        return new SystemSwitchService(null) {
            @Override
            public boolean shouldRecordAuthenticationOperationLogs() { return enabled; }
        };
    }

    private AfterCommitExecutor afterCommitExecutor() {
        return new AfterCommitExecutor() {
            @Override
            public void run(Runnable action) {
                if (action != null) action.run();
            }
        };
    }

    private StringRedisTemplate stringRedisTemplate() {
        return null;
    }

    private static RefreshTokenSession token(Long id) {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setId(id);
        session.setUserId(42L);
        session.setTokenId("token-" + id);
        session.setTokenHash("hash-" + id);
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        return session;
    }
}
