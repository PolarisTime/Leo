package com.leo.erp.security.support;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record SecurityPrincipal(
        Long id,
        String username,
        String password,
        boolean enabled,
        Collection<? extends GrantedAuthority> authorities,
        boolean totpEnabled,
        boolean forceTotpSetup
) implements UserDetails {

    public SecurityPrincipal(
            Long id,
            String username,
            String password,
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this(id, username, password, enabled, authorities, false, false);
    }

    public static SecurityPrincipal system() {
        return new SecurityPrincipal(0L, "system", "", true, List.of(), false, false);
    }

    public static SecurityPrincipal authenticated(
            Long id,
            String username,
            Collection<? extends GrantedAuthority> authorities
    ) {
        return authenticated(id, username, authorities, false, false);
    }

    public static SecurityPrincipal authenticated(
            Long id,
            String username,
            Collection<? extends GrantedAuthority> authorities,
            boolean totpEnabled,
            boolean forceTotpSetup
    ) {
        return new SecurityPrincipal(id, username, "", true, authorities, totpEnabled, forceTotpSetup);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
