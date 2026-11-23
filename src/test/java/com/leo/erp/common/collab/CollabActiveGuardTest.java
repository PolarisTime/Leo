package com.leo.erp.common.collab;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollabActiveGuardTest {

    @Test
    void assertNotActive_throws409CollabActiveWhenKeyExists() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey("collab:active:supplier-price-list:42")).thenReturn(Boolean.TRUE);
        CollabActiveGuard guard = new CollabActiveGuard(redis);

        assertThatThrownBy(() -> guard.assertNotActive("supplier-price-list:42"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COLLAB_ACTIVE));
    }

    @Test
    void assertNotActive_allowsWhenKeyMissingOrRedisAbsentOrFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey("collab:active:supplier-price-list:42")).thenReturn(Boolean.FALSE);
        assertThatCode(() -> new CollabActiveGuard(redis).assertNotActive("supplier-price-list:42"))
                .doesNotThrowAnyException();

        assertThatCode(() -> new CollabActiveGuard(null).assertNotActive("supplier-price-list:42"))
                .doesNotThrowAnyException();

        when(redis.hasKey("collab:active:supplier-price-list:99")).thenThrow(new RuntimeException("redis down"));
        assertThatCode(() -> new CollabActiveGuard(redis).assertNotActive("supplier-price-list:99"))
                .doesNotThrowAnyException();
    }
}
