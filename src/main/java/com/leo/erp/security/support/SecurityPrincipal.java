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
        long credentialVersion
) implements UserDetails {

    public SecurityPrincipal(
            Long id,
            String username,
            String password,
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this(id, username, password, enabled, authorities, 0L);
    }

    public static SecurityPrincipal system() {
        return new SecurityPrincipal(0L, "system", "", true, List.of(), 0L);
    }

    public static SecurityPrincipal authenticated(
            Long id,
            String username,
            Collection<? extends GrantedAuthority> authorities
    ) {
        return authenticated(id, username, authorities, 0L);
    }

    public static SecurityPrincipal authenticated(
            Long id,
            String username,
            Collection<? extends GrantedAuthority> authorities,
            long credentialVersion
    ) {
        return new SecurityPrincipal(
                id,
                username,
                "",
                true,
                authorities,
                credentialVersion
        );
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
