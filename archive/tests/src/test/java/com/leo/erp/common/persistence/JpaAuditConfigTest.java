package com.leo.erp.common.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JpaAuditConfigTest {

    @Test
    void auditorAware_returnsZeroWhenNoAuth() {
        SecurityContextHolder.clearContext();
        JpaAuditConfig config = new JpaAuditConfig();

        AuditorAware<Long> auditorAware = config.auditorAware();
        Optional<Long> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).isPresent();
        assertThat(auditor.get()).isEqualTo(0L);
    }

    @Test
    void auditorAware_returnsZeroWhenNotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        JpaAuditConfig config = new JpaAuditConfig();
        AuditorAware<Long> auditorAware = config.auditorAware();
        Optional<Long> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).isPresent();
        assertThat(auditor.get()).isEqualTo(0L);

        SecurityContextHolder.clearContext();
    }

    @Test
    void auditorAware_beanCreation() {
        JpaAuditConfig config = new JpaAuditConfig();
        AuditorAware<Long> auditorAware = config.auditorAware();
        assertThat(auditorAware).isNotNull();
    }
}
