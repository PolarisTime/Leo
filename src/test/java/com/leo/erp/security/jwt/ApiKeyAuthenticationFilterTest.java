package com.leo.erp.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.domain.entity.ApiKey;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.ApiKeyStatus;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.ApiKeyRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.auth.service.UserRoleBindingService;
import com.leo.erp.auth.support.ApiKeySupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

class ApiKeyAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughWhenApiKeyHeaderMissing() throws ServletException, IOException {
        AtomicBoolean repositoryTouched = new AtomicBoolean(false);
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyRepository(Optional.empty(), repositoryTouched),
                userAccountRepository(Optional.empty(), new AtomicBoolean(false)),
                objectMapper(),
                new UserRoleBindingService(userRoleRepository(), roleSettingRepository(), new NoOpIdGenerator()),
                mock(ApiKeyUsageService.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sales-orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(repositoryTouched.get()).isFalse();
    }

    @Test
    void shouldReturnUnauthorizedWhenApiKeyInvalid() throws ServletException, IOException {
        AtomicBoolean repositoryTouched = new AtomicBoolean(false);
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyRepository(Optional.empty(), repositoryTouched),
                userAccountRepository(Optional.empty(), new AtomicBoolean(false)),
                objectMapper(),
                new UserRoleBindingService(userRoleRepository(), roleSettingRepository(), new NoOpIdGenerator()),
                mock(ApiKeyUsageService.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sales-orders");
        request.addHeader("X-API-Key", "invalid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("API Key 无效或已失效");
        assertThat(repositoryTouched.get()).isTrue();
    }

    @Test
    void shouldReturnForbiddenWhenReadOnlyKeyInvokesWriteRequest() throws ServletException, IOException {
        ApiKey apiKey = activeApiKey(ApiKeySupport.SCOPE_READ_ONLY);
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyRepository(Optional.of(apiKey), new AtomicBoolean(true)),
                userAccountRepository(Optional.of(activeUser()), new AtomicBoolean(false)),
                objectMapper(),
                new UserRoleBindingService(userRoleRepository(), roleSettingRepository(), new NoOpIdGenerator()),
                mock(ApiKeyUsageService.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sales-orders");
        request.addHeader("X-API-Key", "readonly-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("当前 API Key 使用范围不允许访问该接口");
    }

    @Test
    void shouldReturnForbiddenWhenBusinessKeyAccessesSystemEndpoint() throws ServletException, IOException {
        ApiKey apiKey = activeApiKey(ApiKeySupport.SCOPE_BUSINESS);
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyRepository(Optional.of(apiKey), new AtomicBoolean(true)),
                userAccountRepository(Optional.of(activeUser()), new AtomicBoolean(false)),
                objectMapper(),
                new UserRoleBindingService(userRoleRepository(), roleSettingRepository(), new NoOpIdGenerator()),
                mock(ApiKeyUsageService.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/database/status");
        request.addHeader("X-API-Key", "business-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("当前 API Key 使用范围不允许访问该接口");
    }

    @Test
    void shouldReturnForbiddenWhenAllowedResourcesDoesNotContainResolvedResource() throws ServletException, IOException {
        ApiKey apiKey = activeApiKey(ApiKeySupport.SCOPE_ALL);
        apiKey.setAllowedResources("customer");
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyRepository(Optional.of(apiKey), new AtomicBoolean(true)),
                userAccountRepository(Optional.of(activeUser()), new AtomicBoolean(false)),
                objectMapper(),
                new UserRoleBindingService(userRoleRepository(), roleSettingRepository(), new NoOpIdGenerator()),
                mock(ApiKeyUsageService.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sales-orders");
        request.addHeader("X-API-Key", "valid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("当前 API Key 未开通该资源接口权限");
    }

    @Test
    void shouldReturnForbiddenWhenAllowedActionsMissing() throws ServletException, IOException {
        ApiKey apiKey = activeApiKey(ApiKeySupport.SCOPE_ALL);
        apiKey.setAllowedResources("sales-order");
        apiKey.setAllowedActions("");
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyRepository(Optional.of(apiKey), new AtomicBoolean(true)),
                userAccountRepository(Optional.of(activeUser()), new AtomicBoolean(false)),
                objectMapper(),
                new UserRoleBindingService(userRoleRepository(), roleSettingRepository(), new NoOpIdGenerator()),
                mock(ApiKeyUsageService.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sales-orders");
        request.addHeader("X-API-Key", "valid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("当前 API Key 未配置动作权限");
    }

    @Test
    void shouldAuthenticateAndUpdateLastUsedTimeWhenApiKeyValid() throws ServletException, IOException {
        ApiKey apiKey = activeApiKey(ApiKeySupport.SCOPE_ALL);
        apiKey.setAllowedResources("sales-order");
        AtomicBoolean userRepositoryTouched = new AtomicBoolean(false);
        ApiKeyUsageService apiKeyUsageService = mock(ApiKeyUsageService.class);
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyRepository(Optional.of(apiKey), new AtomicBoolean(true)),
                userAccountRepository(Optional.of(activeUser()), userRepositoryTouched),
                objectMapper(),
                new UserRoleBindingService(userRoleRepository(), roleSettingRepository(), new NoOpIdGenerator()),
                apiKeyUsageService
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sales-orders");
        request.addHeader("X-API-Key", "valid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(userRepositoryTouched.get()).isTrue();
        verify(apiKeyUsageService).markUsed(apiKey.getId());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isInstanceOf(SecurityPrincipal.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails()).isInstanceOf(ApiKeyAuthenticationDetails.class);
        SecurityPrincipal principal = (SecurityPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ApiKeyAuthenticationDetails details = (ApiKeyAuthenticationDetails) SecurityContextHolder.getContext().getAuthentication().getDetails();
        assertThat(principal.id()).isEqualTo(1001L);
        assertThat(details.allowedResources()).containsExactly("sales-order");
        assertThat(details.allowedActions()).containsExactly("read", "create", "update", "delete", "export");
    }

    @Test
    void shouldNotRecordUsageWhenApiKeyRejected() throws ServletException, IOException {
        ApiKey apiKey = activeApiKey(ApiKeySupport.SCOPE_READ_ONLY);
        ApiKeyUsageService apiKeyUsageService = mock(ApiKeyUsageService.class);
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyRepository(Optional.of(apiKey), new AtomicBoolean(true)),
                userAccountRepository(Optional.of(activeUser()), new AtomicBoolean(false)),
                objectMapper(),
                new UserRoleBindingService(userRoleRepository(), roleSettingRepository(), new NoOpIdGenerator()),
                apiKeyUsageService
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sales-orders");
        request.addHeader("X-API-Key", "readonly-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(apiKeyUsageService);
    }

    private ApiKey activeApiKey(String usageScope) {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(1L);
        apiKey.setUserId(1001L);
        apiKey.setUsageScope(usageScope);
        apiKey.setAllowedResources("");
        apiKey.setAllowedActions("read,create,update,delete,export");
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey.setExpiresAt(LocalDateTime.now().plusDays(1));
        apiKey.setKeyHash(ApiKeySupport.hashKey("valid-key"));
        return apiKey;
    }

    private UserAccount activeUser() {
        UserAccount user = new UserAccount();
        user.setId(1001L);
        user.setLoginName("api-user");
        user.setPasswordHash("encoded-password");
        user.setStatus(UserStatus.NORMAL);
        return user;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private ApiKeyRepository apiKeyRepository(Optional<ApiKey> apiKey, AtomicBoolean touched) {
        return (ApiKeyRepository) Proxy.newProxyInstance(
                ApiKeyRepository.class.getClassLoader(),
                new Class[]{ApiKeyRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByKeyHashAndDeletedFlagFalse" -> {
                        touched.set(true);
                        yield apiKey;
                    }
                    case "toString" -> "ApiKeyRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountRepository userAccountRepository(Optional<UserAccount> user, AtomicBoolean touched) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> {
                        touched.set(true);
                        yield user;
                    }
                    case "toString" -> "UserAccountRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserRoleRepository userRoleRepository() {
        return (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByUserIdAndDeletedFlagFalse" -> java.util.List.of();
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RoleSettingRepository roleSettingRepository() {
        return (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdInAndDeletedFlagFalse", "findByRoleCodeInAndDeletedFlagFalse", "findByRoleNameInAndDeletedFlagFalse" -> java.util.List.of();
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class NoOpIdGenerator extends SnowflakeIdGenerator {

        @Override
        public synchronized long nextId() {
            return 1L;
        }
    }
}
