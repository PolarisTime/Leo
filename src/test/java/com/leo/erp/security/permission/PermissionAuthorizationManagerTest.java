package com.leo.erp.security.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * {@link PermissionAuthorizationManager} 授权决策测试。
 */
class PermissionAuthorizationManagerTest {

    private final PermissionAuthorizationManager manager = new PermissionAuthorizationManager();

    @Test
    void check_shouldAllowUnannotatedMethod() throws Exception {
        AuthorizationDecision decision = authorize(null, method(Guarded.class, "open"));

        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void check_shouldDenyWhenUnauthenticated() throws Exception {
        AuthorizationDecision decision = authorize(null, method(Guarded.class, "write"));

        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void check_shouldDenyWhenAuthorityMissing() throws Exception {
        AuthorizationDecision decision = authorize(
                authentication(PermissionCodes.SALES_RETURNS_READ),
                method(Guarded.class, "write"));

        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void check_shouldAllowWhenAuthorityPresent() throws Exception {
        AuthorizationDecision decision = authorize(
                authentication(PermissionCodes.SALES_RETURNS_UPDATE),
                method(Guarded.class, "write"));

        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void check_shouldAllowWhenAnyOfMultipleRequiredPresent() throws Exception {
        AuthorizationDecision decision = authorize(
                authentication(PermissionCodes.MATERIALS_UPDATE),
                method(Guarded.class, "either"));

        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void check_shouldAllowWildcardAuthority() throws Exception {
        AuthorizationDecision decision = authorize(
                authentication(PermissionCodes.WILDCARD),
                method(Guarded.class, "write"));

        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void check_shouldAllowClassLevelAnnotation() throws Exception {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(method(AdminGuarded.class, "manage"));
        when(invocation.getThis()).thenReturn(new AdminGuarded());

        AuthorizationDecision decision = manager.check(
                () -> authentication(PermissionCodes.SYSTEM_ADMIN), invocation);

        assertThat(decision.isGranted()).isTrue();
    }

    private AuthorizationDecision authorize(Authentication authentication, Method method) {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(method);
        Supplier<Authentication> supplier = () -> authentication;
        return manager.check(supplier, invocation);
    }

    private Authentication authentication(String... authorities) {
        List<SimpleGrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken("tester", "n/a", granted);
    }

    private Method method(Class<?> type, String name) throws NoSuchMethodException {
        return type.getMethod(name);
    }

    static class Guarded {

        @RequirePermission(PermissionCodes.SALES_RETURNS_UPDATE)
        public void write() {
        }

        @RequirePermission({PermissionCodes.MATERIALS_UPDATE, PermissionCodes.INVENTORY_BACKFILL})
        public void either() {
        }

        public void open() {
        }
    }

    @RequirePermission(PermissionCodes.SYSTEM_ADMIN)
    static class AdminGuarded {

        public void manage() {
        }
    }
}
