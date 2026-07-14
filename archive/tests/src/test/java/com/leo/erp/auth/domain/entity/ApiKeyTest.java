package com.leo.erp.auth.domain.entity;

import com.leo.erp.auth.domain.enums.ApiKeyStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyTest {

    @Test
    void shouldSetAndGetAllFields() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(1L);
        apiKey.setUserId(100L);
        apiKey.setKeyName("测试密钥");
        apiKey.setKeyPrefix("lk_abc");
        apiKey.setKeyHash("hashed_key_value");
        apiKey.setUsageScope("API");
        apiKey.setAllowedResources("order,product");
        apiKey.setAllowedActions("read,write");
        apiKey.setExpiresAt(LocalDateTime.of(2027, 1, 1, 0, 0));
        apiKey.setLastUsedAt(LocalDateTime.of(2026, 6, 1, 12, 0));
        apiKey.setStatus(ApiKeyStatus.ACTIVE);

        assertThat(apiKey.getId()).isEqualTo(1L);
        assertThat(apiKey.getUserId()).isEqualTo(100L);
        assertThat(apiKey.getKeyName()).isEqualTo("测试密钥");
        assertThat(apiKey.getKeyPrefix()).isEqualTo("lk_abc");
        assertThat(apiKey.getKeyHash()).isEqualTo("hashed_key_value");
        assertThat(apiKey.getUsageScope()).isEqualTo("API");
        assertThat(apiKey.getAllowedResources()).isEqualTo("order,product");
        assertThat(apiKey.getAllowedActions()).isEqualTo("read,write");
        assertThat(apiKey.getExpiresAt()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0));
        assertThat(apiKey.getLastUsedAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 12, 0));
        assertThat(apiKey.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    void shouldHandleNullValues() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(null);
        apiKey.setUserId(null);
        apiKey.setKeyName(null);
        apiKey.setKeyPrefix(null);
        apiKey.setKeyHash(null);
        apiKey.setUsageScope(null);
        apiKey.setAllowedResources(null);
        apiKey.setAllowedActions(null);
        apiKey.setExpiresAt(null);
        apiKey.setLastUsedAt(null);
        apiKey.setStatus(null);

        assertThat(apiKey.getId()).isNull();
        assertThat(apiKey.getUserId()).isNull();
        assertThat(apiKey.getKeyName()).isNull();
        assertThat(apiKey.getKeyPrefix()).isNull();
        assertThat(apiKey.getKeyHash()).isNull();
        assertThat(apiKey.getUsageScope()).isNull();
        assertThat(apiKey.getAllowedResources()).isNull();
        assertThat(apiKey.getAllowedActions()).isNull();
        assertThat(apiKey.getExpiresAt()).isNull();
        assertThat(apiKey.getLastUsedAt()).isNull();
        assertThat(apiKey.getStatus()).isNull();
    }

    @Test
    void shouldBeActiveWhenStatusActiveAndNoExpiration() {
        ApiKey apiKey = new ApiKey();
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey.setExpiresAt(null);

        assertThat(apiKey.isActive()).isTrue();
    }

    @Test
    void shouldBeActiveWhenStatusActiveAndNotExpired() {
        ApiKey apiKey = new ApiKey();
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey.setExpiresAt(LocalDateTime.now().plusDays(30));

        assertThat(apiKey.isActive()).isTrue();
    }

    @Test
    void shouldNotBeActiveWhenStatusDisabled() {
        ApiKey apiKey = new ApiKey();
        apiKey.setStatus(ApiKeyStatus.DISABLED);
        apiKey.setExpiresAt(null);

        assertThat(apiKey.isActive()).isFalse();
    }

    @Test
    void shouldNotBeActiveWhenExpired() {
        ApiKey apiKey = new ApiKey();
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey.setExpiresAt(LocalDateTime.now().minusDays(1));

        assertThat(apiKey.isActive()).isFalse();
    }

    @Test
    void shouldSupportAllApiKeyStatuses() {
        ApiKey apiKey = new ApiKey();

        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        assertThat(apiKey.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(apiKey.getStatus().displayName()).isEqualTo("有效");

        apiKey.setStatus(ApiKeyStatus.DISABLED);
        assertThat(apiKey.getStatus()).isEqualTo(ApiKeyStatus.DISABLED);
        assertThat(apiKey.getStatus().displayName()).isEqualTo("已禁用");
    }
}
