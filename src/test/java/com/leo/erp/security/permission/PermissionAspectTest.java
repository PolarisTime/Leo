package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.jwt.ApiKeyAuthenticationDetails;
import com.leo.erp.security.support.SecurityPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionAspectTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        DataScopeContext.clear();
    }

    @Test
    void shouldRejectApiKeyWhenResourceNotAllowed() {
        PermissionAspect aspect = new PermissionAspect(permissionService(true));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal(),
                null,
                List.of()
        );
        authentication.setDetails(new ApiKeyAuthenticationDetails(null, List.of("customer"), List.of("read")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> aspect.checkPermission(joinPoint(new AtomicBoolean(false)), requiresPermission("sales-order", "read")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 未开通该资源接口权限");
    }

    @Test
    void shouldRejectApiKeyWhenActionNotAllowed() {
        PermissionAspect aspect = new PermissionAspect(permissionService(true));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal(),
                null,
                List.of()
        );
        authentication.setDetails(new ApiKeyAuthenticationDetails(null, List.of("sales-order"), List.of("read")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> aspect.checkPermission(joinPoint(new AtomicBoolean(false)), requiresPermission("sales-order", "update")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 未开通该动作权限");
    }

    @Test
    void shouldRejectApiKeyWhenActionsNotConfigured() {
        PermissionAspect aspect = new PermissionAspect(permissionService(true));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal(),
                null,
                List.of()
        );
        authentication.setDetails(new ApiKeyAuthenticationDetails(null, List.of("sales-order"), List.of()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> aspect.checkPermission(joinPoint(new AtomicBoolean(false)), requiresPermission("sales-order", "read")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 未配置动作权限");
    }

    @Test
    void shouldProceedWhenApiKeyMenuAndActionAllowed() throws Throwable {
        AtomicBoolean proceeded = new AtomicBoolean(false);
        PermissionAspect aspect = new PermissionAspect(permissionService(true));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal(),
                null,
                List.of()
        );
        authentication.setDetails(new ApiKeyAuthenticationDetails(null, List.of("sales-order"), List.of("read")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Object result = aspect.checkPermission(joinPoint(proceeded), requiresPermission("sales-order", "read"));

        assertThat(result).isNull();
        assertThat(proceeded.get()).isTrue();
    }

    @Test
    void shouldRestorePreviousDataScopeContextAfterProceeding() throws Throwable {
        AtomicBoolean proceeded = new AtomicBoolean(false);
        PermissionAspect aspect = new PermissionAspect(permissionService(true));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal(),
                null,
                List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        DataScopeContext.set(9L, "receipt", ResourcePermissionCatalog.SCOPE_SELF, Set.of(9L));

        aspect.checkPermission(joinPoint(proceeded), requiresPermission("sales-order", "read"));

        assertThat(proceeded.get()).isTrue();
        assertThat(DataScopeContext.current()).isNotNull();
        assertThat(DataScopeContext.current().userId()).isEqualTo(9L);
        assertThat(DataScopeContext.current().resource()).isEqualTo("receipt");
        assertThat(DataScopeContext.current().scope()).isEqualTo(ResourcePermissionCatalog.SCOPE_SELF);
        assertThat(DataScopeContext.current().ownerUserIds()).containsExactly(9L);
    }

    @Test
    void shouldRejectApiKeyForAuthenticatedOnlyEndpointByDefault() {
        PermissionAspect aspect = new PermissionAspect(permissionService(true));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal(),
                null,
                List.of()
        );
        authentication.setDetails(new ApiKeyAuthenticationDetails(null, List.of("sales-order"), List.of("read")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> aspect.checkPermission(joinPoint(new AtomicBoolean(false)), authenticatedOnly(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key 不允许访问该接口");
    }

    @Test
    void shouldAllowApiKeyForExplicitAuthenticatedOnlyEndpoint() throws Throwable {
        AtomicBoolean proceeded = new AtomicBoolean(false);
        PermissionAspect aspect = new PermissionAspect(permissionService(true));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal(),
                null,
                List.of()
        );
        authentication.setDetails(new ApiKeyAuthenticationDetails(null, List.of("sales-order"), List.of("read")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Object result = aspect.checkPermission(joinPoint(proceeded), authenticatedOnly(true));

        assertThat(result).isNull();
        assertThat(proceeded.get()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private ProceedingJoinPoint joinPoint(AtomicBoolean proceeded) {
        return (ProceedingJoinPoint) Proxy.newProxyInstance(
                ProceedingJoinPoint.class.getClassLoader(),
                new Class[]{ProceedingJoinPoint.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "proceed" -> {
                        proceeded.set(true);
                        yield null;
                    }
                    case "toString" -> "ProceedingJoinPointStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RequiresPermission requiresPermission(String resource, String action) {
        return (RequiresPermission) Proxy.newProxyInstance(
                RequiresPermission.class.getClassLoader(),
                new Class[]{RequiresPermission.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "resource" -> resource;
                    case "action" -> action;
                    case "authenticatedOnly" -> false;
                    case "allowApiKey" -> false;
                    case "annotationType" -> RequiresPermission.class;
                    case "toString" -> "@RequiresPermission";
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RequiresPermission authenticatedOnly(boolean allowApiKey) {
        return (RequiresPermission) Proxy.newProxyInstance(
                RequiresPermission.class.getClassLoader(),
                new Class[]{RequiresPermission.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "resource", "action" -> "";
                    case "authenticatedOnly" -> true;
                    case "allowApiKey" -> allowApiKey;
                    case "annotationType" -> RequiresPermission.class;
                    case "toString" -> "@RequiresPermission";
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PermissionService permissionService(boolean allowed) {
        return new PermissionService(null, null, null, null, null, null) {
            @Override
            public boolean can(Long userId, String resourceCode, String actionCode) {
                return allowed;
            }

            @Override
            public String getUserDataScope(Long userId, String resourceCode) {
                return "all";
            }

            @Override
            public String getUserDataScope(Long userId, String resourceCode, String actionCode) {
                return "all";
            }
        };
    }

    private SecurityPrincipal principal() {
        return new SecurityPrincipal(1L, "api-user", "", true, List.of());
    }
}
