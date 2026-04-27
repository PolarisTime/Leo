package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.ChangeOwnPasswordRequest;
import com.leo.erp.auth.web.dto.CurrentUserSecurityResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountSecurityServiceTest {

    @Test
    void shouldChangePasswordWhenCurrentPasswordMatches() {
        UserAccount account = user(1L, "leo", "encoded:Old@123");
        AtomicReference<UserAccount> savedAccount = new AtomicReference<>();
        AccountSecurityService service = new AccountSecurityService(
                repository(account, savedAccount),
                new StubPasswordEncoder(),
                null,
                authenticatedUserCacheService(),
                null
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
                null
        );

        assertThatThrownBy(() -> service.changePassword(1L, new ChangeOwnPasswordRequest("Wrong@123", "New@123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前密码错误");
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
                null
        );

        CurrentUserSecurityResponse response = service.disable2fa(1L);

        assertThat(savedAccount.get()).isNotNull();
        assertThat(savedAccount.get().getTotpSecret()).isNull();
        assertThat(savedAccount.get().getTotpEnabled()).isFalse();
        assertThat(response.totpEnabled()).isFalse();
        assertThat(response.forceTotpSetup()).isFalse();
    }

    private UserAccountRepository repository(UserAccount account, AtomicReference<UserAccount> savedAccount) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(account);
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
        return new AuthenticatedUserCacheService(null, null, null, null) {
            @Override
            public void evict(Long userId) {
            }
        };
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
