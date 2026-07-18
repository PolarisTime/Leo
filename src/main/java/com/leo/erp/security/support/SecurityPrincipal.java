package com.leo.erp.security.support;

import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public record SecurityPrincipal(
        Long id,
        String username,
        String password,
        boolean enabled,
        long credentialVersion
) implements UserDetails {

    public static SecurityPrincipal system() {
        return new SecurityPrincipal(0L, "system", "", true, 0L);
    }

    public static SecurityPrincipal authenticated(
            Long id,
            String username,
            long credentialVersion
    ) {
        return new SecurityPrincipal(
                id,
                username,
                "",
                true,
                credentialVersion
        );
    }

    @Override
    public List<org.springframework.security.core.GrantedAuthority> getAuthorities() {
        return List.of();
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
