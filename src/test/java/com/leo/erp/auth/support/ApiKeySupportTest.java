package com.leo.erp.auth.support;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeySupportTest {

    @Test
    void shouldHashKey() {
        String rawKey = "test-api-key";
        String hash = ApiKeySupport.hashKey(rawKey);

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 hex is 64 chars
    }

    @Test
    void shouldProduceSameHashForSameInput() {
        String rawKey = "test-api-key";
        String hash1 = ApiKeySupport.hashKey(rawKey);
        String hash2 = ApiKeySupport.hashKey(rawKey);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void shouldProduceDifferentHashForDifferentInput() {
        String hash1 = ApiKeySupport.hashKey("key1");
        String hash2 = ApiKeySupport.hashKey("key2");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void shouldWrapUnavailableSha256Algorithm() {
        try (var digest = Mockito.mockStatic(MessageDigest.class)) {
            digest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("missing"));

            assertThatThrownBy(() -> ApiKeySupport.hashKey("key"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHA-256不可用")
                    .hasCauseInstanceOf(NoSuchAlgorithmException.class);
        }
    }

    @Test
    void shouldParseAllowedResources() {
        List<String> resources = ApiKeySupport.parseAllowedResources("user,role,menu");

        assertThat(resources).containsExactly("user", "role", "menu");
    }

    @Test
    void shouldParseAllowedResourcesWithWhitespace() {
        List<String> resources = ApiKeySupport.parseAllowedResources(" user , role , menu ");

        assertThat(resources).containsExactly("user", "role", "menu");
    }

    @Test
    void shouldParseAllowedResourcesRemovingDuplicates() {
        List<String> resources = ApiKeySupport.parseAllowedResources("user,role,user,menu,role");

        assertThat(resources).containsExactly("user", "role", "menu");
    }

    @Test
    void shouldIgnoreBlankItemsWhenParsingAllowedResources() {
        List<String> resources = ApiKeySupport.parseAllowedResources(" user, ,role,, ");

        assertThat(resources).containsExactly("user", "role");
    }

    @Test
    void shouldReturnEmptyListForNullResources() {
        List<String> resources = ApiKeySupport.parseAllowedResources(null);

        assertThat(resources).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForBlankResources() {
        List<String> resources = ApiKeySupport.parseAllowedResources("  ");

        assertThat(resources).isEmpty();
    }

    @Test
    void shouldJoinAllowedResources() {
        String joined = ApiKeySupport.joinAllowedResources(List.of("user", "role", "menu"));

        assertThat(joined).isEqualTo("user,role,menu");
    }

    @Test
    void shouldJoinAllowedResourcesWithWhitespace() {
        String joined = ApiKeySupport.joinAllowedResources(List.of(" user ", " role "));

        assertThat(joined).isEqualTo("user,role");
    }

    @Test
    void shouldIgnoreBlankItemsWhenJoiningAllowedResources() {
        String joined = ApiKeySupport.joinAllowedResources(List.of(" user ", " ", "role", ""));

        assertThat(joined).isEqualTo("user,role");
    }

    @Test
    void shouldReturnEmptyStringForNullList() {
        String joined = ApiKeySupport.joinAllowedResources(null);

        assertThat(joined).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringForEmptyList() {
        String joined = ApiKeySupport.joinAllowedResources(List.of());

        assertThat(joined).isEmpty();
    }

    @Test
    void shouldParseAllowedActions() {
        List<String> actions = ApiKeySupport.parseAllowedActions("read,write,delete");

        assertThat(actions).containsExactly("read", "write", "delete");
    }

    @Test
    void shouldJoinAllowedActions() {
        String joined = ApiKeySupport.joinAllowedActions(List.of("read", "write"));

        assertThat(joined).isEqualTo("read,write");
    }

    @Test
    void shouldNormalizeAllowedResources() {
        List<String> resources = ApiKeySupport.normalizeAllowedResources(
                Arrays.asList(" Material ", null, "material", " ", "SUPPLIER")
        );

        assertThat(resources).containsExactly("material", "supplier");
    }

    @Test
    void shouldRejectNullAllowedResources() {
        assertThatThrownBy(() -> ApiKeySupport.normalizeAllowedResources(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API Key 允许访问资源不能为空");
    }

    @Test
    void shouldRejectEmptyAllowedResources() {
        assertThatThrownBy(() -> ApiKeySupport.normalizeAllowedResources(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API Key 允许访问资源不能为空");
    }

    @Test
    void shouldRejectBlankOnlyAllowedResources() {
        assertThatThrownBy(() -> ApiKeySupport.normalizeAllowedResources(List.of(" ", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API Key 允许访问资源不能为空");
    }

    @Test
    void shouldRejectUnknownAllowedResource() {
        assertThatThrownBy(() -> ApiKeySupport.normalizeAllowedResources(List.of("missing-resource")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API Key 允许访问资源不合法");
    }

    @Test
    void shouldNormalizeAllowedActions() {
        List<String> actions = ApiKeySupport.normalizeAllowedActions(
                Arrays.asList(" view ", null, "READ", " ", "edit")
        );

        assertThat(actions).containsExactly("read", "update");
    }

    @Test
    void shouldRejectNullAllowedActions() {
        assertThatThrownBy(() -> ApiKeySupport.normalizeAllowedActions(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API Key 允许动作不能为空");
    }

    @Test
    void shouldRejectEmptyAllowedActions() {
        assertThatThrownBy(() -> ApiKeySupport.normalizeAllowedActions(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API Key 允许动作不能为空");
    }

    @Test
    void shouldRejectBlankOnlyAllowedActions() {
        assertThatThrownBy(() -> ApiKeySupport.normalizeAllowedActions(List.of(" ", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API Key 允许动作不能为空");
    }

    @Test
    void shouldRejectUnknownAllowedAction() {
        assertThatThrownBy(() -> ApiKeySupport.normalizeAllowedActions(List.of("missing-action")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API Key 允许动作不合法");
    }

    @Test
    void shouldHaveCorrectScopeConstants() {
        assertThat(ApiKeySupport.SCOPE_ALL).isEqualTo("全部接口");
        assertThat(ApiKeySupport.SCOPE_READ_ONLY).isEqualTo("只读接口");
        assertThat(ApiKeySupport.SCOPE_BUSINESS).isEqualTo("业务接口");
    }

    @Test
    void shouldHaveAllowedUsageScope() {
        assertThat(ApiKeySupport.ALLOWED_USAGE_SCOPE).containsExactlyInAnyOrder(
                "全部接口", "只读接口", "业务接口"
        );
    }
}
