package com.leo.erp.auth.service;

import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.ChangeOwnPasswordRequest;
import com.leo.erp.auth.web.dto.CurrentUserSecurityResponse;
import com.leo.erp.auth.web.dto.TotpSetupResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountSecurityServiceTest {

    @Test
    void shouldIncrementCredentialVersionAndRevokeChangedUsersSessions() {
        UserAccount account = user(1L, "leo", "encoded:Old@123");
        account.setCredentialVersion(3L);
        UserAccountRepository repository = mock(UserAccountRepository.class);
        when(repository.findByIdAndDeletedFlagFalseForUpdate(1L)).thenReturn(Optional.of(account));
        when(repository.save(account)).thenReturn(account);
        SessionManagementService sessionManagementService = mock(SessionManagementService.class);
        AccountSecurityService service = new AccountSecurityService(
                repository,
                new StubPasswordEncoder(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                sessionManagementService
        );

        service.changePassword(1L, new ChangeOwnPasswordRequest("Old@123", "New@123"));

        assertThat(account.getCredentialVersion()).isEqualTo(4L);
        verify(repository).findByIdAndDeletedFlagFalseForUpdate(1L);
        verify(sessionManagementService).revokeActiveSessionsForPasswordChange(1L);
    }

    @Test
    void shouldChangePasswordWhenCurrentPasswordMatches() {
        UserAccount account = user(1L, "leo", "encoded:Old@123");
        AtomicReference<UserAccount> savedAccount = new AtomicReference<>();
        AccountSecurityService service = new AccountSecurityService(
                repository(account, savedAccount),
                new StubPasswordEncoder(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                mock(SessionManagementService.class)
        );

        service.changePassword(1L, new ChangeOwnPasswordRequest("Old@123", "New@123"));

        assertThat(savedAccount.get()).isNotNull();
        assertThat(savedAccount.get().getPasswordHash()).isEqualTo("encoded:New@123");
    }

    @Test
    void shouldRejectPasswordChangeWhenCurrentPasswordIsWrong() {
        UserAccount account = user(1L, "leo", "encoded:Old@123");
        AccountSecurityService service = new AccountSecurityService(
                repository(account, new AtomicReference<>()),
                new StubPasswordEncoder(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                mock(SessionManagementService.class)
        );

        assertThatThrownBy(() -> service.changePassword(1L, new ChangeOwnPasswordRequest("Wrong@123", "New@123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前密码错误");
    }

    @Test
    void shouldRejectPasswordChangeWhenNewPasswordSameAsCurrent() {
        UserAccount account = user(1L, "leo", "encoded:Same@123");
        AccountSecurityService service = new AccountSecurityService(
                repository(account, new AtomicReference<>()),
                new StubPasswordEncoder(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                mock(SessionManagementService.class)
        );

        assertThatThrownBy(() -> service.changePassword(1L, new ChangeOwnPasswordRequest("Same@123", "Same@123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新密码不能与当前密码相同");
    }

    @Test
    void shouldDisable2faWhenRequested() {
        UserAccount account = user(1L, "leo", "encoded:Old@123");
        account.setTotpSecret("encrypted-secret");
        account.setTotpEnabled(Boolean.TRUE);
        AtomicReference<UserAccount> savedAccount = new AtomicReference<>();
        AccountSecurityService service = new AccountSecurityService(
                repository(account, savedAccount),
                new StubPasswordEncoder(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                mock(SessionManagementService.class)
        );

        CurrentUserSecurityResponse response = service.disable2fa(1L);

        assertThat(savedAccount.get()).isNotNull();
        assertThat(savedAccount.get().getTotpSecret()).isNull();
        assertThat(savedAccount.get().getTotpEnabled()).isFalse();
        assertThat(response.totpEnabled()).isFalse();
        assertThat(response.forceTotpSetup()).isFalse();
    }

    @Test
    void shouldDisable2faWhenSystemSwitchAllowsAndEvictDashboard() {
        UserAccount account = user(1L, "leo", "encoded:Old@123");
        account.setTotpSecret("encrypted-secret");
        account.setTotpEnabled(Boolean.TRUE);
        AtomicReference<UserAccount> savedAccount = new AtomicReference<>();
        DashboardSummaryService dashboardSummaryService = mock(DashboardSummaryService.class);
        com.leo.erp.system.norule.service.SystemSwitchService switchService =
                mock(com.leo.erp.system.norule.service.SystemSwitchService.class);
        when(switchService.shouldForbidDisable2fa()).thenReturn(false);
        AccountSecurityService service = new AccountSecurityService(
                repository(account, savedAccount),
                new StubPasswordEncoder(),
                null,
                authenticatedUserCacheService(),
                dashboardSummaryService,
                switchService,
                mock(SessionManagementService.class)
        );

        CurrentUserSecurityResponse response = service.disable2fa(1L);

        assertThat(savedAccount.get().getTotpEnabled()).isFalse();
        assertThat(response.forbidDisable2fa()).isFalse();
        verify(dashboardSummaryService).evictCache(1L);
    }

    @Test
    void shouldRejectDisable2faWhenSystemForbidden() {
        com.leo.erp.system.norule.service.SystemSwitchService switchService =
                mock(com.leo.erp.system.norule.service.SystemSwitchService.class);
        when(switchService.shouldForbidDisable2fa()).thenReturn(true);

        AccountSecurityService service = new AccountSecurityService(
                repository(user(1L, "leo", "encoded:pass"), new AtomicReference<>()),
                new StubPasswordEncoder(),
                null,
                authenticatedUserCacheService(),
                null,
                switchService,
                mock(SessionManagementService.class)
        );

        assertThatThrownBy(() -> service.disable2fa(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统设置禁止关闭 2FA");
    }

    @Test
    void shouldSetup2faAndReturnQrCode() {
        UserAccount account = user(1L, "leo", "encoded:pass");
        AtomicReference<UserAccount> savedAccount = new AtomicReference<>();
        StubTotpService totpService = new StubTotpService();

        AccountSecurityService service = new AccountSecurityService(
                repository(account, savedAccount),
                new StubPasswordEncoder(),
                totpService,
                authenticatedUserCacheService(),
                null,
                null,
                mock(SessionManagementService.class)
        );

        TotpSetupResponse response = service.setup2fa(1L);

        assertThat(response.qrCodeBase64()).isNotBlank();
        assertThat(response.secret()).isEqualTo("test-secret");
        assertThat(savedAccount.get().getTotpSecret()).isEqualTo("encrypted:test-secret");
        assertThat(savedAccount.get().getTotpEnabled()).isFalse();
    }

    @Test
    void shouldRejectSetup2faWhenAlreadyEnabled() {
        UserAccount account = user(1L, "leo", "encoded:pass");
        account.setTotpSecret("encrypted-secret");
        account.setTotpEnabled(Boolean.TRUE);

        AccountSecurityService service = new AccountSecurityService(
                repository(account, new AtomicReference<>()),
                new StubPasswordEncoder(),
                new StubTotpService(),
                authenticatedUserCacheService(),
                null,
                null,
                mock(SessionManagementService.class)
        );

        assertThatThrownBy(() -> service.setup2fa(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2FA 已启用");
    }

    @Test
    void shouldEnable2faWhenValidCodeProvided() {
        UserAccount account = user(1L, "leo", "encoded:pass");
        account.setTotpSecret("encrypted-secret");
        AtomicReference<UserAccount> savedAccount = new AtomicReference<>();
        StubTotpService totpService = new StubTotpService();
        totpService.setVerifyResult(true);

        AccountSecurityService service = new AccountSecurityService(
                repository(account, savedAccount),
                new StubPasswordEncoder(),
                totpService,
                authenticatedUserCacheService(),
                null,
                null,
                mock(SessionManagementService.class)
        );

        CurrentUserSecurityResponse response = service.enable2fa(1L,
                new com.leo.erp.auth.web.dto.TotpEnableRequest("123456"));

        assertThat(response.totpEnabled()).isTrue();
        assertThat(response.forceTotpSetup()).isFalse();
        assertThat(savedAccount.get().getTotpEnabled()).isTrue();
        assertThat(savedAccount.get().getRequireTotpSetup()).isFalse();
    }

    @Test
    void shouldRejectEnable2faWhenNoSecret() {
        UserAccount account = user(1L, "leo", "encoded:pass");
        account.setTotpSecret(null);

        AccountSecurityService service = new AccountSecurityService(
                repository(account, new AtomicReference<>()),
                new StubPasswordEncoder(),
                new StubTotpService(),
                authenticatedUserCacheService(),
                null,
                null,
                mock(SessionManagementService.class)
        );

        assertThatThrownBy(() -> service.enable2fa(1L,
                new com.leo.erp.auth.web.dto.TotpEnableRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先生成2FA密钥");
    }

    @Test
    void shouldRejectEnable2faWhenCodeInvalid() {
        UserAccount account = user(1L, "leo", "encoded:pass");
        account.setTotpSecret("encrypted-secret");
        StubTotpService totpService = new StubTotpService();
        totpService.setVerifyResult(false);

        AccountSecurityService service = new AccountSecurityService(
                repository(account, new AtomicReference<>()),
                new StubPasswordEncoder(),
                totpService,
                authenticatedUserCacheService(),
                null,
                null,
                mock(SessionManagementService.class)
        );

        assertThatThrownBy(() -> service.enable2fa(1L,
                new com.leo.erp.auth.web.dto.TotpEnableRequest("000000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码错误或已过期");
    }

    @Test
    void shouldReturnSecurityStatus() {
        UserAccount account = user(1L, "leo", "encoded:pass");
        account.setTotpEnabled(Boolean.TRUE);
        account.setRequireTotpSetup(Boolean.FALSE);

        AccountSecurityService service = new AccountSecurityService(
                repository(account, new AtomicReference<>()),
                new StubPasswordEncoder(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                mock(SessionManagementService.class)
        );

        CurrentUserSecurityResponse response = service.getStatus(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.loginName()).isEqualTo("leo");
        assertThat(response.userName()).isEqualTo("Leo");
        assertThat(response.totpEnabled()).isTrue();
        assertThat(response.forceTotpSetup()).isFalse();
    }

    @Test
    void shouldReturnForbidDisable2faWhenSystemSet() {
        com.leo.erp.system.norule.service.SystemSwitchService switchService =
                mock(com.leo.erp.system.norule.service.SystemSwitchService.class);
        when(switchService.shouldForbidDisable2fa()).thenReturn(true);

        AccountSecurityService service = new AccountSecurityService(
                repository(user(1L, "leo", "encoded:pass"), new AtomicReference<>()),
                new StubPasswordEncoder(),
                null,
                authenticatedUserCacheService(),
                null,
                switchService,
                mock(SessionManagementService.class)
        );

        CurrentUserSecurityResponse response = service.getStatus(1L);

        assertThat(response.forbidDisable2fa()).isTrue();
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        AccountSecurityService service = new AccountSecurityService(
                notFoundRepository(),
                new StubPasswordEncoder(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                mock(SessionManagementService.class)
        );

        assertThatThrownBy(() -> service.changePassword(999L, new ChangeOwnPasswordRequest("Old@123", "New@123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    private UserAccountRepository repository(UserAccount account, AtomicReference<UserAccount> savedAccount) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse", "findByIdAndDeletedFlagFalseForUpdate" -> Optional.of(account);
                    case "save" -> {
                        savedAccount.set((UserAccount) args[0]);
                        yield args[0];
                    }
                    case "toString" -> "UserAccountRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccount user(Long id, String loginName, String passwordHash) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setLoginName(loginName);
        account.setUserName("Leo");
        account.setPasswordHash(passwordHash);
        account.setRequireTotpSetup(Boolean.FALSE);
        return account;
    }

    private AuthenticatedUserCacheService authenticatedUserCacheService() {
        return new AuthenticatedUserCacheService(null, null, null, null, new RedisTuningProperties()) {
            @Override
            public void evict(Long userId) {
            }
        };
    }

    private UserAccountRepository notFoundRepository() {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse", "findByIdAndDeletedFlagFalseForUpdate" -> Optional.empty();
                    case "toString" -> "UserAccountRepositoryNotFoundStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class StubTotpService extends com.leo.erp.auth.service.TotpService {
        private boolean verifyResult = false;

        private StubTotpService() {
            super(new com.leo.erp.security.totp.TotpProperties("test", null), null, null);
        }

        @Override
        public String generateSecret() {
            return "test-secret";
        }

        @Override
        public String encryptSecret(String plainSecret) {
            return "encrypted:" + plainSecret;
        }

        @Override
        public String decryptSecret(String encryptedSecret) {
            return "decrypted-secret";
        }

        @Override
        public boolean verifyCode(String secret, String code) {
            return verifyResult;
        }

        @Override
        public byte[] generateQrCodeImage(String secret, String loginName) {
            return "fake-qr-bytes".getBytes();
        }

        void setVerifyResult(boolean result) {
            this.verifyResult = result;
        }
    }

    private static final class StubPasswordEncoder implements org.springframework.security.crypto.password.PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encodedPassword.equals(encode(rawPassword));
        }
    }
}
