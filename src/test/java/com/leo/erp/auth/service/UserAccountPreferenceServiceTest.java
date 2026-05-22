package com.leo.erp.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.UserAccountPreferencesPayload;
import com.leo.erp.auth.web.dto.UserListColumnSettingsPayload;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountPreferenceServiceTest {

    @Test
    void shouldReturnEmptyPreferencesWhenNoPayloadIsSaved() {
        UserAccount account = user(1L);
        UserAccountPreferenceService service = new UserAccountPreferenceService(
                repository(account, new AtomicReference<>()),
                new ObjectMapper()
        );

        UserAccountPreferencesPayload response = service.getPreferences(1L);

        assertThat(response.pages()).isEmpty();
    }

    @Test
    void shouldNormalizeAndPersistPreferencesPayload() {
        UserAccount account = user(1L);
        AtomicReference<String> savedPreferences = new AtomicReference<>();
        UserAccountPreferenceService service = new UserAccountPreferenceService(
                repository(account, new AtomicReference<>(), savedPreferences),
                new ObjectMapper()
        );
        List<String> hiddenKeys = new ArrayList<>();
        hiddenKeys.add(" status ");
        hiddenKeys.add("status");
        hiddenKeys.add(null);
        hiddenKeys.add(" ");

        UserAccountPreferencesPayload saved = service.savePreferences(1L, new UserAccountPreferencesPayload(Map.of(
                " sales-order ", new UserListColumnSettingsPayload(
                        List.of(" orderNo ", "", "orderNo", "customerName"),
                        hiddenKeys
                ),
                " ", new UserListColumnSettingsPayload(List.of("ignored"), List.of())
        )));

        assertThat(saved.pages()).containsOnlyKeys("sales-order");
        assertThat(saved.pages().get("sales-order").orderedKeys())
                .containsExactly("orderNo", "customerName");
        assertThat(saved.pages().get("sales-order").hiddenKeys())
                .containsExactly("status");
        assertThat(savedPreferences.get()).isNotBlank();
        assertThat(service.getPreferences(1L)).isEqualTo(saved);
    }

    @Test
    void shouldIgnoreInvalidSavedJsonPayload() {
        UserAccount account = user(1L);
        account.setPreferencesJson("{invalid");
        UserAccountPreferenceService service = new UserAccountPreferenceService(
                repository(account, new AtomicReference<>()),
                new ObjectMapper()
        );

        UserAccountPreferencesPayload response = service.getPreferences(1L);

        assertThat(response.pages()).isEmpty();
    }

    private UserAccountRepository repository(UserAccount account, AtomicReference<UserAccount> savedAccount) {
        return repository(account, savedAccount, new AtomicReference<>());
    }

    private UserAccountRepository repository(
            UserAccount account,
            AtomicReference<UserAccount> savedAccount,
            AtomicReference<String> savedPreferences
    ) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(account);
                    case "save" -> {
                        savedAccount.set((UserAccount) args[0]);
                        yield args[0];
                    }
                    case "updatePreferencesJson" -> {
                        account.setPreferencesJson((String) args[1]);
                        savedPreferences.set((String) args[1]);
                        yield 1;
                    }
                    case "toString" -> "UserAccountRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccount user(Long id) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setLoginName("tester");
        account.setUserName("测试用户");
        account.setPasswordHash("encoded");
        return account;
    }
}
