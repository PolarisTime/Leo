package com.leo.erp.security.support;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPrincipalTest {

    @Test
    void shouldCreateSystemPrincipal() {
        SecurityPrincipal principal = SecurityPrincipal.system();

        assertThat(principal.id()).isEqualTo(0L);
        assertThat(principal.username()).isEqualTo("system");
        assertThat(principal.password()).isEmpty();
        assertThat(principal.enabled()).isTrue();
        assertThat(principal.authorities()).isEmpty();
        assertThat(principal.totpEnabled()).isFalse();
        assertThat(principal.forceTotpSetup()).isFalse();
    }

    @Test
    void shouldCreateAuthenticatedPrincipal() {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(
                1001L,
                "admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(principal.id()).isEqualTo(1001L);
        assertThat(principal.username()).isEqualTo("admin");
        assertThat(principal.password()).isEmpty();
        assertThat(principal.enabled()).isTrue();
        assertThat(principal.authorities()).hasSize(1);
        assertThat(principal.totpEnabled()).isFalse();
        assertThat(principal.forceTotpSetup()).isFalse();
    }

    @Test
    void shouldCreateAuthenticatedPrincipalWithTotp() {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(
                1001L,
                "admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true,
                true
        );

        assertThat(principal.id()).isEqualTo(1001L);
        assertThat(principal.username()).isEqualTo("admin");
        assertThat(principal.totpEnabled()).isTrue();
        assertThat(principal.forceTotpSetup()).isTrue();
    }

    @Test
    void shouldCreatePrincipalWithConstructor() {
        SecurityPrincipal principal = new SecurityPrincipal(
                1001L,
                "admin",
                "encoded",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                true,
                false
        );

        assertThat(principal.id()).isEqualTo(1001L);
        assertThat(principal.username()).isEqualTo("admin");
        assertThat(principal.password()).isEqualTo("encoded");
        assertThat(principal.enabled()).isTrue();
        assertThat(principal.authorities()).hasSize(1);
        assertThat(principal.totpEnabled()).isTrue();
        assertThat(principal.forceTotpSetup()).isFalse();
    }

    @Test
    void shouldImplementUserDetailsMethods() {
        SecurityPrincipal principal = new SecurityPrincipal(
                1001L,
                "admin",
                "encoded",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                false,
                false
        );

        assertThat(principal.getUsername()).isEqualTo("admin");
        assertThat(principal.getPassword()).isEqualTo("encoded");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
    }
}