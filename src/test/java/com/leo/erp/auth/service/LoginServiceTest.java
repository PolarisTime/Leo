package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LoginResponseBody;
import com.leo.erp.auth.web.dto.LoginStep1Response;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.operationlog.service.OperationLogCommand;
import com.leo.erp.system.operationlog.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginServiceTest {

    private static final LoginService.AuthRequestContext CTX =
            new LoginService.AuthRequestContext("127.0.0.1", "JUnit", "/auth/login", "POST");

    @Test
    void loginShouldReturnTokenWhenCredentialsValid() {
        UserAccount user = normalUser();
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByLoginNameAndDeletedFlagFalse("admin")).thenReturn(java.util.Optional.of(user));

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("secret", "hash")).thenReturn(true);

        LoginAttemptService attemptService = mock(LoginAttemptService.class);
        TokenIssuanceService tokenIssuance = mock(TokenIssuanceService.class);
        TokenResponse tokenResponse = new TokenResponse(
                "access", "refresh", "Bearer", 300, 1800, null
        );
        when(tokenIssuance.issueTokens(any(), anyString(), anyString())).thenReturn(tokenResponse);

        LoginService service = new LoginService(
                userRepo, encoder, mock(TotpService.class), attemptService,
                mock(StringRedisTemplate.class), tokenIssuance,
                mock(OperationLogService.class), mock(SystemSwitchService.class)
        );

        LoginResponseBody response = service.login(new LoginRequest("admin", "secret", null, null), CTX);

        assertThat(response).isInstanceOf(TokenResponse.class);
        verify(attemptService).clearFailures("admin");
    }

    @Test
    void loginShouldReturnStep1ResponseWhenTotpEnabled() {
        UserAccount user = normalUser();
        user.setTotpEnabled(true);
        user.setTotpSecret("encrypted-secret");

        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByLoginNameAndDeletedFlagFalse("admin")).thenReturn(java.util.Optional.of(user));

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("secret", "hash")).thenReturn(true);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        LoginService service = new LoginService(
                userRepo, encoder, mock(TotpService.class), mock(LoginAttemptService.class),
                redis, mock(TokenIssuanceService.class),
                mock(OperationLogService.class), mock(SystemSwitchService.class)
        );

        LoginResponseBody response = service.login(new LoginRequest("admin", "secret", null, null), CTX);

        assertThat(response).isInstanceOf(LoginStep1Response.class);
        assertThat(((LoginStep1Response) response).requires2fa()).isTrue();
    }

    @Test
    void loginShouldThrowWhenUserNotFound() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByLoginNameAndDeletedFlagFalse("unknown")).thenReturn(java.util.Optional.empty());

        LoginService service = new LoginService(
                userRepo, mock(PasswordEncoder.class), mock(TotpService.class), mock(LoginAttemptService.class),
                mock(StringRedisTemplate.class), mock(TokenIssuanceService.class),
                mock(OperationLogService.class), mock(SystemSwitchService.class)
        );

        assertThatThrownBy(() -> service.login(new LoginRequest("unknown", "pass", null, null), CTX))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginShouldThrowWhenAccountDisabled() {
        UserAccount user = normalUser();
        user.setStatus(UserStatus.DISABLED);

        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByLoginNameAndDeletedFlagFalse("admin")).thenReturn(java.util.Optional.of(user));

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("secret", "hash")).thenReturn(true);

        LoginService service = new LoginService(
                userRepo, encoder, mock(TotpService.class), mock(LoginAttemptService.class),
                mock(StringRedisTemplate.class), mock(TokenIssuanceService.class),
                mock(OperationLogService.class), mock(SystemSwitchService.class)
        );

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "secret", null, null), CTX))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void loginShouldThrowWhenPasswordIncorrect() {
        UserAccount user = normalUser();
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByLoginNameAndDeletedFlagFalse("admin")).thenReturn(java.util.Optional.of(user));

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("wrong", "hash")).thenReturn(false);

        LoginAttemptService attemptService = mock(LoginAttemptService.class);

        LoginService service = new LoginService(
                userRepo, encoder, mock(TotpService.class), attemptService,
                mock(StringRedisTemplate.class), mock(TokenIssuanceService.class),
                mock(OperationLogService.class), mock(SystemSwitchService.class)
        );

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "wrong", null, null), CTX))
                .isInstanceOf(BadCredentialsException.class);

        verify(attemptService).recordFailure("admin");
    }

    @Test
    void verifyTotpShouldThrowWhenTempTokenExpired() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(valueOps.get("auth:2fa:temp:expired-token")).thenReturn(null);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        LoginService service = new LoginService(
                null, null, mock(TotpService.class), mock(LoginAttemptService.class),
                redis, mock(TokenIssuanceService.class),
                mock(OperationLogService.class), mock(SystemSwitchService.class)
        );

        assertThatThrownBy(() -> service.verifyTotpAndIssueTokens("expired-token", "123456", CTX))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void verifyTotpShouldReturnTokensWhenCodeValid() {
        UserAccount user = normalUser();
        user.setTotpEnabled(true);
        user.setTotpSecret("encrypted-secret");

        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByIdAndDeletedFlagFalse(1L)).thenReturn(java.util.Optional.of(user));

        TotpService totpService = mock(TotpService.class);
        when(totpService.decryptSecret("encrypted-secret")).thenReturn("secret");
        when(totpService.verifyCode("secret", "123456")).thenReturn(true);

        LoginAttemptService attemptService = mock(LoginAttemptService.class);
        TokenIssuanceService tokenIssuance = mock(TokenIssuanceService.class);
        TokenResponse tokenResponse = new TokenResponse("access", "refresh", "Bearer", 300, 1800, null);
        when(tokenIssuance.issueTokens(any(), anyString(), anyString())).thenReturn(tokenResponse);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(valueOps.get("auth:2fa:temp:valid-token")).thenReturn("1");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        LoginService service = new LoginService(
                userRepo, mock(PasswordEncoder.class), totpService, attemptService,
                redis, tokenIssuance,
                mock(OperationLogService.class), mock(SystemSwitchService.class)
        );

        TokenResponse response = service.verifyTotpAndIssueTokens("valid-token", "123456", CTX);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access");
        verify(attemptService).clearFailures("admin");
    }

    @Test
    void verifyTotpShouldThrowWhenUserNotFound() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByIdAndDeletedFlagFalse(99L)).thenReturn(java.util.Optional.empty());

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(valueOps.get("auth:2fa:temp:valid-token")).thenReturn("99");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        LoginService service = new LoginService(
                userRepo, mock(PasswordEncoder.class), mock(TotpService.class), mock(LoginAttemptService.class),
                redis, mock(TokenIssuanceService.class),
                mock(OperationLogService.class), mock(SystemSwitchService.class)
        );

        assertThatThrownBy(() -> service.verifyTotpAndIssueTokens("valid-token", "123456", CTX))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void verifyTotpShouldThrowWhenAccountDisabled() {
        UserAccount user = normalUser();
        user.setStatus(UserStatus.DISABLED);
        user.setTotpEnabled(true);
        user.setTotpSecret("encrypted-secret");

        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByIdAndDeletedFlagFalse(1L)).thenReturn(java.util.Optional.of(user));

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(valueOps.get("auth:2fa:temp:valid-token")).thenReturn("1");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        LoginService service = new LoginService(
                userRepo, mock(PasswordEncoder.class), mock(TotpService.class), mock(LoginAttemptService.class),
                redis, mock(TokenIssuanceService.class),
                mock(OperationLogService.class), mock(SystemSwitchService.class)
        );

        assertThatThrownBy(() -> service.verifyTotpAndIssueTokens("valid-token", "123456", CTX))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void verifyTotpShouldThrowWhenCodeInvalid() {
        UserAccount user = normalUser();
        user.setTotpEnabled(true);
        user.setTotpSecret("encrypted-secret");

        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByIdAndDeletedFlagFalse(1L)).thenReturn(java.util.Optional.of(user));

        TotpService totpService = mock(TotpService.class);
        when(totpService.decryptSecret("encrypted-secret")).thenReturn("secret");
        when(totpService.verifyCode("secret", "000000")).thenReturn(false);

        LoginAttemptService attemptService = mock(LoginAttemptService.class);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(valueOps.get("auth:2fa:temp:valid-token")).thenReturn("1");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        LoginService service = new LoginService(
                userRepo, mock(PasswordEncoder.class), totpService, attemptService,
                redis, mock(TokenIssuanceService.class),
                mock(OperationLogService.class), mock(SystemSwitchService.class)
        );

        assertThatThrownBy(() -> service.verifyTotpAndIssueTokens("valid-token", "000000", CTX))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("验证码错误或已过期");

        verify(attemptService).recordFailure("admin");
    }

    @Test
    void loginShouldNormalizeBlankLoginName() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByLoginNameAndDeletedFlagFalse("")).thenReturn(java.util.Optional.empty());

        LoginAttemptService attemptService = mock(LoginAttemptService.class);
        LoginService service = new LoginService(
                userRepo, mock(PasswordEncoder.class), mock(TotpService.class), attemptService,
                mock(StringRedisTemplate.class), mock(TokenIssuanceService.class),
                mock(OperationLogService.class), mock(SystemSwitchService.class)
        );

        assertThatThrownBy(() -> service.login(new LoginRequest("  ", "pass", null, null), CTX))
                .isInstanceOf(BadCredentialsException.class);

        verify(attemptService).ensureLoginAllowed("");
    }

    @Test
    void recordAuthenticationLogShouldSkipWhenServiceNull() {
        LoginService service = new LoginService(
                null, null, null, null, null, null, null, null
        );

        org.assertj.core.api.Assertions.assertThatCode(() ->
                service.recordAuthenticationLog("登录", null, "admin", CTX, "成功", "测试")
        ).doesNotThrowAnyException();
    }

    @Test
    void recordAuthenticationLogShouldSkipWhenSwitchDisabled() {
        SystemSwitchService switchService = mock(SystemSwitchService.class);
        when(switchService.shouldRecordAuthenticationOperationLogs()).thenReturn(false);

        OperationLogService logService = mock(OperationLogService.class);

        LoginService service = new LoginService(
                null, null, null, null, null, null, logService, switchService
        );

        service.recordAuthenticationLog("登录", null, "admin", CTX, "成功", "测试");

        verify(logService, org.mockito.Mockito.never()).record(any());
    }

    @Test
    void recordAuthenticationLogShouldUseDefaultMethodWhenBlank() {
        SystemSwitchService switchService = mock(SystemSwitchService.class);
        when(switchService.shouldRecordAuthenticationOperationLogs()).thenReturn(true);

        java.util.List<OperationLogCommand> commands = new java.util.ArrayList<>();
        OperationLogService logService = new OperationLogService(null, null, null, null) {
            @Override
            public void record(OperationLogCommand command) { commands.add(command); }
        };

        LoginService service = new LoginService(
                null, null, null, null, null, null, logService, switchService
        );

        LoginService.AuthRequestContext ctxNoMethod = new LoginService.AuthRequestContext(
                "127.0.0.1", "JUnit", null, null);

        service.recordAuthenticationLog("登录", null, "admin", ctxNoMethod, "成功", "测试");

        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).requestMethod()).isEqualTo("POST");
        assertThat(commands.get(0).requestPath()).isEqualTo("/auth/login");
    }

    @Test
    void recordAuthenticationLogShouldUseLogoutPathForLogoutAction() {
        SystemSwitchService switchService = mock(SystemSwitchService.class);
        when(switchService.shouldRecordAuthenticationOperationLogs()).thenReturn(true);

        java.util.List<OperationLogCommand> commands = new java.util.ArrayList<>();
        OperationLogService logService = new OperationLogService(null, null, null, null) {
            @Override
            public void record(OperationLogCommand command) { commands.add(command); }
        };

        LoginService service = new LoginService(
                null, null, null, null, null, null, logService, switchService
        );

        LoginService.AuthRequestContext ctxNoPath = new LoginService.AuthRequestContext(
                "127.0.0.1", "JUnit", "", "POST");

        service.recordAuthenticationLog("退出登录", null, "admin", ctxNoPath, "成功", "退出成功");

        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).requestPath()).isEqualTo("/auth/logout");
    }

    private UserAccount normalUser() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setLoginName("admin");
        user.setUserName("管理员");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.NORMAL);
        user.setTotpEnabled(false);
        return user;
    }
}
