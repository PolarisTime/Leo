package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.ApiKey;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.ApiKeyStatus;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.ApiKeyRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.ApiKeyRequest;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

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
    void generateShouldValidateKeyNameAndExpireDays() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user(1L, UserStatus.NORMAL, true)));

        ApiKeyAdminService service = service(apiKeyRepository, userAccountRepository);
        loginAs(1L);

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
