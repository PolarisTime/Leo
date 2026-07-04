package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.ApiKey;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.ApiKeyStatus;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.ApiKeyRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.support.ApiKeySupport;
import com.leo.erp.auth.web.dto.ApiKeyRequest;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyAdminServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectUnknownStatusFilter() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );
        loginAs(1L);

        assertThatThrownBy(() -> service.page(new com.leo.erp.common.api.PageQuery(0, 20, null, null), null, null, "unknown", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 状态不合法");
    }

    @Test
    void shouldRejectUnknownStatusWhenBuildingStatusSpecDirectly() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "buildStatusSpec", "unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 状态不合法");
    }

    @Test
    void shouldRejectUnknownUsageScopeFilter() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );
        loginAs(1L);

        assertThatThrownBy(() -> service.page(new com.leo.erp.common.api.PageQuery(0, 20, null, null), null, null, null, "unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 使用范围不合法");
    }

    @Test
    void shouldRejectUnknownAllowedResources() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
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
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
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
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "normalizeAllowedActions", List.of("UNKNOWN")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 允许动作不合法");
    }

    @Test
    void shouldRejectBlankStatusWhenBuildingStatusSpecDirectly() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "buildStatusSpec", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 状态不能为空");
    }

    @Test
    void shouldRejectNullStatusWhenNormalizingDirectly() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "normalizeStatus", (Object) null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 状态不能为空");
    }

    @Test
    void shouldBuildStatusSpecifications() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );

        executeApiKeySpec(ReflectionTestUtils.invokeMethod(service, "buildStatusSpec", "有效"));
        executeApiKeySpec(ReflectionTestUtils.invokeMethod(service, "buildStatusSpec", "已过期"));
        executeApiKeySpec(ReflectionTestUtils.invokeMethod(service, "buildStatusSpec", "已禁用"));
    }

    @Test
    void shouldNormalizeAllowedResourcesAndActionsToJoinedText() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );

        assertThat((String) ReflectionTestUtils.invokeMethod(
                service,
                "normalizeAllowedResources",
                List.of(" sales-order ", "purchase-order")
        )).isEqualTo("sales-order,purchase-order");
        assertThat((String) ReflectionTestUtils.invokeMethod(
                service,
                "normalizeAllowedActions",
                List.of(" READ ", "print")
        )).isEqualTo("read,print");
    }

    @Test
    void shouldWrapAllowedResourceAndActionJoinFailures() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                repository(ApiKeyRepository.class),
                repository(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );

        try (MockedStatic<ApiKeySupport> support = Mockito.mockStatic(ApiKeySupport.class, Mockito.CALLS_REAL_METHODS)) {
            support.when(() -> ApiKeySupport.joinAllowedResources(List.of("sales-order")))
                    .thenThrow(new IllegalArgumentException("资源拼接失败"));
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    service,
                    "normalizeAllowedResources",
                    List.of("sales-order")
            ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("资源拼接失败");
        }

        try (MockedStatic<ApiKeySupport> support = Mockito.mockStatic(ApiKeySupport.class, Mockito.CALLS_REAL_METHODS)) {
            support.when(() -> ApiKeySupport.joinAllowedActions(List.of("read")))
                    .thenThrow(new IllegalArgumentException("动作拼接失败"));
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    service,
                    "normalizeAllowedActions",
                    List.of("read")
            ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("动作拼接失败");
        }
    }

    @Test
    void shouldRejectWhenNotLoggedIn() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                mock(ApiKeyRepository.class),
                mock(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );

        assertThatThrownBy(() -> service.page(new com.leo.erp.common.api.PageQuery(0, 20, null, null), null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    void shouldRejectAuthenticationWithoutSecurityPrincipal() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                mock(ApiKeyRepository.class),
                mock(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("demo", null, List.of())
        );

        assertThatThrownBy(() -> service.page(new com.leo.erp.common.api.PageQuery(0, 20, null, null), null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    void shouldListAvailableUsers() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        UserAccount user = user(7L, UserStatus.NORMAL, true);
        when(userAccountRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<UserAccount>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        ApiKeyAdminService service = new ApiKeyAdminService(
                mock(ApiKeyRepository.class),
                userAccountRepository,
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );

        List<com.leo.erp.auth.web.dto.ApiKeyUserOptionResponse> users = service.listAvailableUsers(" demo ");

        assertThat(users).hasSize(1);
        assertThat(users.get(0).id()).isEqualTo(7L);
        assertThat(users.get(0).loginName()).isEqualTo("demo7");
        assertThat(users.get(0).userName()).isEqualTo("Demo 7");
    }

    @Test
    void shouldBuildAvailableUserSpecification() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<UserAccount>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(invocation -> {
                    Specification<UserAccount> spec = invocation.getArgument(0);
                    executeUserSpec(spec);
                    return new PageImpl<UserAccount>(List.of());
                });
        ApiKeyAdminService service = new ApiKeyAdminService(
                mock(ApiKeyRepository.class),
                userAccountRepository,
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );

        assertThat(service.listAvailableUsers(" demo ")).isEmpty();
    }

    @Test
    void shouldListResourceAndActionOptions() {
        ApiKeyAdminService service = new ApiKeyAdminService(
                mock(ApiKeyRepository.class),
                mock(UserAccountRepository.class),
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );

        assertThat(service.listResourceOptions())
                .anySatisfy(option -> assertThat(option.code()).isEqualTo("sales-order"));
        assertThat(service.listActionOptions())
                .anySatisfy(option -> assertThat(option.code()).isEqualTo("read"));
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
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );
        loginAs(1L);

        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                "测试密钥",
                "全部接口",
                List.of("sales-order"),
                List.of("read"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户未启用2FA");
    }

    @Test
    void shouldAllowGenerateForCurrentUser() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        UserAccount user = user(1L, UserStatus.NORMAL, true);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        var response = service.generate(1L, new ApiKeyRequest(
                "测试密钥",
                "全部接口",
                List.of("sales-order"),
                List.of("read"),
                null
        ));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.rawKey()).startsWith("leo_");
        verify(apiKeyRepository).save(any());
    }

    @Test
    void detailShouldFallbackToUserIdWhenUserMissing() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ApiKey apiKey = apiKey(99L, 42L, ApiKeyStatus.ACTIVE, null);
        apiKey.setAllowedResources("purchase-order,sales-order");
        apiKey.setAllowedActions("read,print");
        when(apiKeyRepository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.of(apiKey));
        when(userAccountRepository.findByIdAndDeletedFlagFalse(42L)).thenReturn(Optional.empty());

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(42L);

        var response = service.detail(99L);

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.loginName()).isEqualTo("42");
        assertThat(response.userName()).isEqualTo("--");
        assertThat(response.allowedResources()).containsExactly("purchase-order", "sales-order");
        assertThat(response.allowedActions()).containsExactly("read", "print");
        assertThat(response.status()).isEqualTo("有效");
    }

    @Test
    void detailShouldResolveExpiredStatus() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ApiKey apiKey = apiKey(100L, 1L, ApiKeyStatus.ACTIVE, LocalDateTime.now().minusSeconds(1));
        when(apiKeyRepository.findByIdAndDeletedFlagFalse(100L)).thenReturn(Optional.of(apiKey));
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user(1L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        assertThat(service.detail(100L).status()).isEqualTo("已过期");
    }

    @Test
    void detailShouldResolveDisabledStatus() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ApiKey apiKey = apiKey(103L, 1L, ApiKeyStatus.DISABLED, null);
        when(apiKeyRepository.findByIdAndDeletedFlagFalse(103L)).thenReturn(Optional.of(apiKey));
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user(1L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        assertThat(service.detail(103L).status()).isEqualTo("已禁用");
    }

    @Test
    void detailShouldAllowAdminToViewOtherUsersApiKey() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ApiKey apiKey = apiKey(102L, 2L, ApiKeyStatus.ACTIVE, null);
        when(apiKeyRepository.findByIdAndDeletedFlagFalse(102L)).thenReturn(Optional.of(apiKey));
        when(userAccountRepository.findByIdAndDeletedFlagFalse(2L)).thenReturn(Optional.of(user(2L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAsAdmin(1L);

        var response = service.detail(102L);

        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.loginName()).isEqualTo("demo2");
    }

    @Test
    void detailShouldRejectMissingApiKey() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        when(apiKeyRepository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());
        ApiKeyAdminService service = service(apiKeyRepository, mock(UserAccountRepository.class));
        loginAs(1L);

        assertThatThrownBy(() -> service.detail(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 不存在");
    }

    @Test
    void revokeShouldDisableActiveKey() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ApiKey apiKey = apiKey(101L, 1L, ApiKeyStatus.ACTIVE, LocalDateTime.now().plusDays(1));
        when(apiKeyRepository.findByIdAndDeletedFlagFalse(101L)).thenReturn(Optional.of(apiKey));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        service.revoke(101L);

        assertThat(apiKey.getStatus()).isEqualTo(ApiKeyStatus.DISABLED);
        verify(apiKeyRepository).save(apiKey);
    }

    @Test
    void revokeShouldRejectDisabledOrExpiredKey() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(apiKeyRepository.findByIdAndDeletedFlagFalse(1L))
                .thenReturn(Optional.of(apiKey(1L, 1L, ApiKeyStatus.DISABLED, null)));
        when(apiKeyRepository.findByIdAndDeletedFlagFalse(2L))
                .thenReturn(Optional.of(apiKey(2L, 1L, ApiKeyStatus.ACTIVE, LocalDateTime.now().minusDays(1))));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        assertThatThrownBy(() -> service.revoke(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被禁用");
        assertThatThrownBy(() -> service.revoke(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    void nonAdminShouldOnlyViewOwnApiKeys() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ApiKey apiKey = apiKey(99L, 2L, ApiKeyStatus.ACTIVE, null);
        when(apiKeyRepository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.of(apiKey));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        assertThatThrownBy(() -> service.page(new com.leo.erp.common.api.PageQuery(0, 20, null, null), null, 2L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能查看自己的 API Key");
        assertThatThrownBy(() -> service.detail(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能查看自己的 API Key");
    }

    @Test
    void nonAdminShouldOnlyRevokeOwnApiKeys() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ApiKey apiKey = apiKey(99L, 2L, ApiKeyStatus.ACTIVE, null);
        when(apiKeyRepository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.of(apiKey));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        assertThatThrownBy(() -> service.revoke(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能禁用自己的 API Key");
    }

    @Test
    void generateShouldRejectMissingOrDisabledUser() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());
        when(userAccountRepository.findByIdAndDeletedFlagFalse(2L)).thenReturn(Optional.of(user(2L, UserStatus.DISABLED, true)));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        assertThatThrownBy(() -> service.generate(404L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能为当前登录用户生成 API Key");
        assertThatThrownBy(() -> service.generate(2L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能为当前登录用户生成 API Key");
    }

    @Test
    void adminGenerateShouldRejectMissingOrDisabledUserAfterAuthorization() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());
        when(userAccountRepository.findByIdAndDeletedFlagFalse(2L)).thenReturn(Optional.of(user(2L, UserStatus.DISABLED, true)));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAsAdmin(1L);

        assertThatThrownBy(() -> service.generate(404L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户不存在");
        assertThatThrownBy(() -> service.generate(2L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户已禁用");
    }

    @Test
    void generateShouldRejectTotpEnabledUserWithoutSecret() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        UserAccount user = user(1L, UserStatus.NORMAL, true);
        user.setTotpSecret(" ");
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        assertThatThrownBy(() -> service.generate(1L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户未启用2FA");
    }

    @Test
    void generateShouldRejectTotpEnabledUserWithoutSecretValue() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        UserAccount user = user(1L, UserStatus.NORMAL, true);
        user.setTotpSecret(null);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        assertThatThrownBy(() -> service.generate(1L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户未启用2FA");
    }

    @Test
    void generateShouldValidateKeyNameAndExpireDays() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user(1L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                null,
                "全部接口",
                List.of("sales-order"),
                List.of("read"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密钥名称不能为空");
        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                " ",
                "全部接口",
                List.of("sales-order"),
                List.of("read"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密钥名称不能为空");
        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                "x".repeat(65),
                "全部接口",
                List.of("sales-order"),
                List.of("read"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密钥名称长度不能超过64");
        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                "测试密钥",
                "全部接口",
                List.of("sales-order"),
                List.of("read"),
                0L
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("有效天数必须大于0");
        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                "测试密钥",
                "全部接口",
                List.of("sales-order"),
                List.of("read"),
                3651L
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("有效天数不能超过3650");
    }

    @Test
    void generateShouldRejectBlankUsageScope() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user(1L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                "测试密钥",
                null,
                List.of("sales-order"),
                List.of("read"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 使用范围不能为空");
        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                "测试密钥",
                " ",
                List.of("sales-order"),
                List.of("read"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 使用范围不能为空");
    }

    @Test
    void generateShouldNormalizeAndPersistPayload() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user(1L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        var response = service.generate(1L, new ApiKeyRequest(
                "  集成密钥  ",
                " 全部接口 ",
                List.of(" purchase-order ", "purchase-order", "sales-order"),
                List.of(" READ ", "print", "read"),
                7L
        ));

        assertThat(response.keyName()).isEqualTo("集成密钥");
        assertThat(response.usageScope()).isEqualTo("全部接口");
        assertThat(response.allowedResources()).containsExactly("purchase-order", "sales-order");
        assertThat(response.allowedActions()).containsExactly("read", "print");
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now().plusDays(6));
        assertThat(response.rawKey()).startsWith("leo_");
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void generateShouldRejectEmptyAllowedResources() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user(1L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                "测试密钥",
                "全部接口",
                List.of(" "),
                List.of("read"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 允许访问资源不能为空");
    }

    @Test
    void nonAdminShouldNotGenerateForOtherUser() {
        ApiKeyAdminService service = service(mock(ApiKeyRepository.class), mock(UserAccountRepository.class));
        loginAs(1L);

        assertThatThrownBy(() -> service.generate(2L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能为当前登录用户生成 API Key");
    }

    @Test
    void adminShouldGenerateForOtherUserWithinBothPermissionBounds() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(2L)).thenReturn(Optional.of(user(2L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = new ApiKeyAdminService(
                apiKeyRepository,
                userAccountRepository,
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceByUser(Map.of(
                        1L, fullPermissionMap(),
                        2L, Map.of("sales-order", Set.of("read"))
                ))
        );
        loginAsAdmin(1L);

        var response = service.generate(2L, validRequest());

        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.allowedResources()).containsExactly("sales-order");
        assertThat(response.allowedActions()).containsExactly("read");
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void generateShouldRejectResourcesBeyondTargetUserPermissions() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user(1L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = new ApiKeyAdminService(
                apiKeyRepository,
                userAccountRepository,
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceByUser(Map.of(1L, Map.of("sales-order", Set.of("read"))))
        );
        loginAs(1L);

        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                "测试密钥",
                "全部接口",
                List.of("purchase-order"),
                List.of("read"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户权限不足");
    }

    @Test
    void generateShouldRejectEmptyPermissionActionSet() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user(1L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = new ApiKeyAdminService(
                apiKeyRepository,
                userAccountRepository,
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceByUser(Map.of(1L, Map.of("sales-order", Set.of())))
        );
        loginAs(1L);

        assertThatThrownBy(() -> service.generate(1L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户权限不足");
    }

    @Test
    void generateShouldRejectActionsBeyondTargetUserPermissions() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user(1L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = new ApiKeyAdminService(
                apiKeyRepository,
                userAccountRepository,
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceByUser(Map.of(1L, Map.of("sales-order", Set.of("read"))))
        );
        loginAs(1L);

        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                "测试密钥",
                "全部接口",
                List.of("sales-order"),
                List.of("delete"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户权限不足");
    }

    @Test
    void generateShouldRejectCatalogDisallowedActionEvenWhenUserHasAction() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user(1L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = new ApiKeyAdminService(
                apiKeyRepository,
                userAccountRepository,
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceByUser(Map.of(1L, Map.of("permission", Set.of("delete"))))
        );
        loginAs(1L);

        assertThatThrownBy(() -> service.generate(1L, new ApiKeyRequest(
                "测试密钥",
                "全部接口",
                List.of("permission"),
                List.of("delete"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户权限不足");
    }

    @Test
    void adminShouldNotGenerateForOtherUserBeyondOperatorPermissions() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(2L)).thenReturn(Optional.of(user(2L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = new ApiKeyAdminService(
                apiKeyRepository,
                userAccountRepository,
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceByUser(Map.of(
                        1L, Map.of("sales-order", Set.of("read")),
                        2L, fullPermissionMap()
                ))
        );
        loginAsAdmin(1L);

        assertThatThrownBy(() -> service.generate(2L, new ApiKeyRequest(
                "测试密钥",
                "全部接口",
                List.of("purchase-order"),
                List.of("read"),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前操作者权限不足");
    }

    @Test
    void nonAdminShouldPageOwnApiKeysWhenUserIdMatches() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        when(apiKeyRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<ApiKey>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        ApiKeyAdminService service = service(apiKeyRepository, mock(UserAccountRepository.class));
        loginAs(1L);

        assertThat(service.page(new com.leo.erp.common.api.PageQuery(0, 20, null, null), null, 1L, null, null))
                .isEmpty();
    }

    @Test
    void adminShouldPageWithStatusAndUsageScopeFilters() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ApiKey apiKey = apiKey(120L, 2L, ApiKeyStatus.ACTIVE, LocalDateTime.now().plusDays(1));
        when(apiKeyRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<ApiKey>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(apiKey)));
        when(userAccountRepository.findByIdAndDeletedFlagFalse(2L)).thenReturn(Optional.of(user(2L, UserStatus.NORMAL, true)));
        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAsAdmin(1L);

        var page = service.page(
                new com.leo.erp.common.api.PageQuery(0, 20, null, null),
                "leo",
                2L,
                "有效",
                "全部接口"
        );

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).userId()).isEqualTo(2L);
        assertThat(page.getContent().get(0).loginName()).isEqualTo("demo2");
        assertThat(page.getContent().get(0).status()).isEqualTo("有效");
    }

    @Test
    void adminShouldExecuteComposedPageSpecification() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(apiKeyRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<ApiKey>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(invocation -> {
                    Specification<ApiKey> spec = invocation.getArgument(0);
                    executeApiKeySpec(spec);
                    return new PageImpl<ApiKey>(List.of());
                });
        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAsAdmin(1L);

        assertThat(service.page(
                new com.leo.erp.common.api.PageQuery(0, 20, null, null),
                " key ",
                2L,
                "已过期",
                "全部接口"
        )).isEmpty();
    }

    @Test
    void adminShouldPageWithoutUserFilterAndIgnoreBlankFilters() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        when(apiKeyRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<ApiKey>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        ApiKeyAdminService service = service(apiKeyRepository, mock(UserAccountRepository.class));
        loginAsAdmin(1L);

        assertThat(service.page(new com.leo.erp.common.api.PageQuery(0, 20, null, null), null, null, " ", " "))
                .isEmpty();
    }

    @Test
    void shouldIgnoreNullAuthoritiesWhenCheckingAdminRole() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        when(apiKeyRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<ApiKey>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        ApiKeyAdminService service = service(apiKeyRepository, mock(UserAccountRepository.class));
        SecurityPrincipal principal = SecurityPrincipal.authenticated(
                1L,
                "admin",
                List.of(
                        (org.springframework.security.core.GrantedAuthority) () -> null,
                        (org.springframework.security.core.GrantedAuthority) () -> " role_admin "
                ),
                true,
                false
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        assertThat(service.page(new com.leo.erp.common.api.PageQuery(0, 20, null, null), null, 2L, null, null))
                .isEmpty();
    }

    private ApiKeyAdminService service(ApiKeyRepository apiKeyRepository, UserAccountRepository userAccountRepository) {
        return new ApiKeyAdminService(
                apiKeyRepository,
                userAccountRepository,
                new com.leo.erp.common.support.SnowflakeIdGenerator(0L),
                permissionServiceForCurrentUser(fullPermissionMap())
        );
    }

    private ApiKeyRequest validRequest() {
        return new ApiKeyRequest("测试密钥", "全部接口", List.of("sales-order"), List.of("read"), null);
    }

    private ApiKey apiKey(Long id, Long userId, ApiKeyStatus status, LocalDateTime expiresAt) {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(id);
        apiKey.setUserId(userId);
        apiKey.setKeyName("测试密钥");
        apiKey.setUsageScope("全部接口");
        apiKey.setAllowedResources("");
        apiKey.setAllowedActions("read");
        apiKey.setKeyPrefix("leo_1234");
        apiKey.setKeyHash("hash");
        apiKey.setStatus(status);
        apiKey.setExpiresAt(expiresAt);
        apiKey.setCreatedAt(LocalDateTime.now().minusDays(1));
        return apiKey;
    }

    private UserAccount user(Long id, UserStatus status, boolean totpEnabled) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setLoginName("demo" + id);
        user.setUserName("Demo " + id);
        user.setMobile("1380000000" + id);
        user.setStatus(status);
        user.setTotpEnabled(totpEnabled);
        user.setTotpSecret(totpEnabled ? "totp-secret" : null);
        return user;
    }

    private PermissionService permissionServiceForCurrentUser(Map<String, Set<String>> permissionMap) {
        return permissionServiceByUser(Map.of(1L, permissionMap));
    }

    private PermissionService permissionServiceByUser(Map<Long, Map<String, Set<String>>> permissionsByUserId) {
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.getUserPermissionMap(anyLong())).thenAnswer(invocation ->
                permissionsByUserId.getOrDefault(invocation.getArgument(0), Map.of()));
        return permissionService;
    }

    private Map<String, Set<String>> fullPermissionMap() {
        return Map.of(
                "purchase-order", Set.of("read", "print"),
                "sales-order", Set.of("read", "print", "create", "update", "delete", "export")
        );
    }

    private void loginAs(Long userId) {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(userId, "demo" + userId, List.of(), true, false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private void loginAsAdmin(Long userId) {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(
                userId,
                "admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true,
                false
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeApiKeySpec(Specification<ApiKey> spec) {
        Root<ApiKey> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mockCriteriaBuilder();
        Path path = mock(Path.class);
        when(root.get(any(String.class))).thenReturn(path);

        spec.toPredicate(root, query, criteriaBuilder);

        verify(root, org.mockito.Mockito.atLeastOnce()).get(any(String.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeUserSpec(Specification<UserAccount> spec) {
        Root<UserAccount> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mockCriteriaBuilder();
        Path path = mock(Path.class);
        when(root.get(any(String.class))).thenReturn(path);

        spec.toPredicate(root, query, criteriaBuilder);

        verify(root, org.mockito.Mockito.atLeastOnce()).get(any(String.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CriteriaBuilder mockCriteriaBuilder() {
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        when(criteriaBuilder.conjunction()).thenReturn(predicate);
        when(criteriaBuilder.isFalse(any(Expression.class))).thenReturn(predicate);
        when(criteriaBuilder.isTrue(any(Expression.class))).thenReturn(predicate);
        when(criteriaBuilder.isNull(any(Expression.class))).thenReturn(predicate);
        when(criteriaBuilder.isNotNull(any(Expression.class))).thenReturn(predicate);
        when(criteriaBuilder.equal(any(Expression.class), any())).thenReturn(predicate);
        when(criteriaBuilder.like(any(Expression.class), any(String.class))).thenReturn(predicate);
        when(criteriaBuilder.greaterThan(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(criteriaBuilder.lessThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(criteriaBuilder.and(org.mockito.ArgumentMatchers.<Predicate[]>any())).thenReturn(predicate);
        when(criteriaBuilder.or(org.mockito.ArgumentMatchers.<Predicate[]>any())).thenReturn(predicate);
        when(criteriaBuilder.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(criteriaBuilder.or(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        return criteriaBuilder;
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
