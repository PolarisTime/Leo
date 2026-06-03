package com.leo.erp.auth.repository;

import com.leo.erp.auth.domain.entity.ApiKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyRepositoryTest {

    @Mock
    private ApiKeyRepository repository;

    @Test
    void findByKeyHashAndDeletedFlagFalse_shouldReturnApiKeyWhenExists() {
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyHash("hash123");
        apiKey.setKeyName("测试API密钥");
        apiKey.setDeletedFlag(false);

        when(repository.findByKeyHashAndDeletedFlagFalse("hash123")).thenReturn(Optional.of(apiKey));

        Optional<ApiKey> result = repository.findByKeyHashAndDeletedFlagFalse("hash123");

        assertThat(result).isPresent();
        assertThat(result.get().getKeyName()).isEqualTo("测试API密钥");
    }

    @Test
    void findByKeyHashAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByKeyHashAndDeletedFlagFalse("hash456")).thenReturn(Optional.empty());

        Optional<ApiKey> result = repository.findByKeyHashAndDeletedFlagFalse("hash456");

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnApiKeyWhenExistsAndNotDeleted() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(1L);
        apiKey.setKeyHash("hash123");
        apiKey.setKeyName("测试API密钥");
        apiKey.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(apiKey));

        Optional<ApiKey> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getKeyHash()).isEqualTo("hash123");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<ApiKey> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void updateLastUsedAt_shouldUpdateLastUsedAt() {
        LocalDateTime now = LocalDateTime.now();

        when(repository.updateLastUsedAt(eq(1L), any(LocalDateTime.class))).thenReturn(1);

        int updated = repository.updateLastUsedAt(1L, now);

        assertThat(updated).isEqualTo(1);
    }
}
