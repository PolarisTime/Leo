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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void shouldReturnEmptyPreferencesWhenSavedPayloadIsBlank() {
        UserAccount account = user(1L);
        account.setPreferencesJson("  ");
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

    @Test
    void shouldNormalizeNullPayloadAndNullPageSettings() {
        UserAccount account = user(1L);
        AtomicReference<String> savedPreferences = new AtomicReference<>();
        UserAccountPreferenceService service = new UserAccountPreferenceService(
                repository(account, new AtomicReference<>(), savedPreferences),
                new ObjectMapper()
        );

        UserAccountPreferencesPayload empty = service.savePreferences(1L, null);
        UserAccountPreferencesPayload withNullPage = service.savePreferences(1L, new UserAccountPreferencesPayload(
                mapWithNullPageSettings()
        ));

        assertThat(empty.pages()).isEmpty();
        assertThat(withNullPage.pages()).containsOnlyKeys("sales-order");
        assertThat(withNullPage.pages().get("sales-order").orderedKeys()).isEmpty();
        assertThat(withNullPage.pages().get("sales-order").hiddenKeys()).isEmpty();
        assertThat(savedPreferences.get()).isNotBlank();
    }

    @Test
    void shouldNormalizePayloadWithNullPagesAndNullKeyLists() {
        UserAccount account = user(1L);
        AtomicReference<String> savedPreferences = new AtomicReference<>();
        UserAccountPreferenceService service = new UserAccountPreferenceService(
                repository(account, new AtomicReference<>(), savedPreferences),
                new ObjectMapper()
        );

        UserAccountPreferencesPayload nullPages = service.savePreferences(1L, new UserAccountPreferencesPayload(null));
        UserAccountPreferencesPayload nullKeys = service.savePreferences(1L, new UserAccountPreferencesPayload(Map.of(
                "sales-order", new UserListColumnSettingsPayload(null, null)
        )));

        assertThat(nullPages.pages()).isEmpty();
        assertThat(nullKeys.pages()).containsOnlyKeys("sales-order");
        assertThat(nullKeys.pages().get("sales-order").orderedKeys()).isEmpty();
        assertThat(nullKeys.pages().get("sales-order").hiddenKeys()).isEmpty();
        assertThat(savedPreferences.get()).isNotBlank();
    }

    @Test
    void shouldNormalizePayloadWithEmptyKeyLists() {
        UserAccount account = user(1L);
        AtomicReference<String> savedPreferences = new AtomicReference<>();
        UserAccountPreferenceService service = new UserAccountPreferenceService(
                repository(account, new AtomicReference<>(), savedPreferences),
                new ObjectMapper()
        );

        UserAccountPreferencesPayload response = service.savePreferences(1L, new UserAccountPreferencesPayload(Map.of(
                "sales-order", new UserListColumnSettingsPayload(List.of(), List.of())
        )));

        assertThat(response.pages()).containsOnlyKeys("sales-order");
        assertThat(response.pages().get("sales-order").orderedKeys()).isEmpty();
        assertThat(response.pages().get("sales-order").hiddenKeys()).isEmpty();
    }

    @Test
    void shouldRejectMissingUserWhenReadingOrSaving() {
        UserAccountPreferenceService service = new UserAccountPreferenceService(
                repository(null, new AtomicReference<>(), new AtomicReference<>(), 0),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> service.getPreferences(1L))
                .isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .hasMessageContaining("用户不存在");
        assertThatThrownBy(() -> service.savePreferences(1L, new UserAccountPreferencesPayload(Map.of())))
                .isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void shouldWrapSerializationFailure() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(org.mockito.Mockito.any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});
        UserAccountPreferenceService service = new UserAccountPreferenceService(
                repository(user(1L), new AtomicReference<>()),
                objectMapper
        );

        assertThatThrownBy(() -> service.savePreferences(1L, new UserAccountPreferencesPayload(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("序列化用户偏好配置失败");
    }

    private UserAccountRepository repository(UserAccount account, AtomicReference<UserAccount> savedAccount) {
        return repository(account, savedAccount, new AtomicReference<>());
    }

    private UserAccountRepository repository(
            UserAccount account,
            AtomicReference<UserAccount> savedAccount,
            AtomicReference<String> savedPreferences
    ) {
        return repository(account, savedAccount, savedPreferences, 1);
    }

    private UserAccountRepository repository(
            UserAccount account,
            AtomicReference<UserAccount> savedAccount,
            AtomicReference<String> savedPreferences,
            int updateCount
    ) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.ofNullable(account);
                    case "save" -> {
                        savedAccount.set((UserAccount) args[0]);
                        yield args[0];
                    }
                    case "updatePreferencesJson" -> {
                        if (account != null) {
                            account.setPreferencesJson((String) args[1]);
                        }
                        savedPreferences.set((String) args[1]);
                        yield updateCount;
                    }
                    case "toString" -> "UserAccountRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private Map<String, UserListColumnSettingsPayload> mapWithNullPageSettings() {
        Map<String, UserListColumnSettingsPayload> pages = new java.util.LinkedHashMap<>();
        pages.put(" sales-order ", null);
        return pages;
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
