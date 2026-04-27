package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.ApiKeyRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.ApiKeyRequest;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyAdminServiceTest {

    @Test
    void shouldRejectUnknownStatusFilter() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L)
        );

        assertThatThrownBy(() -> service.page(new com.leo.erp.common.api.PageQuery(0, 20, null, null), null, null, "unknown", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 状态不合法");
    }

    @Test
    void shouldRejectUnknownUsageScopeFilter() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L)
        );

        assertThatThrownBy(() -> service.page(new com.leo.erp.common.api.PageQuery(0, 20, null, null), null, null, null, "unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 使用范围不合法");
    }

    @Test
    void shouldRejectUnknownAllowedResources() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L)
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "normalizeAllowedResources", List.of("unknown-resource")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 允许访问资源不合法");
    }

    @Test
    void shouldRejectEmptyAllowedActions() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L)
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "normalizeAllowedActions", List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 允许动作不能为空");
    }

    @Test
    void shouldRejectUnknownAllowedActions() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L)
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "normalizeAllowedActions", List.of("UNKNOWN")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 允许动作不合法");
    }

    @Test
    void shouldRejectGenerateWhenTargetUserHasNotEnabledTotp() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setLoginName("demo");
        user.setUserName("Demo");
        user.setStatus(UserStatus.NORMAL);
        user.setTotpEnabled(Boolean.FALSE);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user));

        ApiKeyAdminService service = new ApiKeyAdminService(
                apiKeyRepository,
                userAccountRepository,
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L)
        );

        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                "测试密钥",
                "全部接口",
                List.of(),
                List.of("read"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户未启用2FA");
    }

    @SuppressWarnings("unchecked")
    private <T> T repository(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class[]{type},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
