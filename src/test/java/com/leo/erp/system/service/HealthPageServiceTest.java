package com.leo.erp.system.service;

import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.database.service.DatabaseStatusService;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HealthPageServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void renderShouldReturnPublicPageWhenNotAuthenticated() {
        DatabaseStatusService dbStatusService = mock(DatabaseStatusService.class);
        PermissionService permissionService = mock(PermissionService.class);
        HealthPageRenderer renderer = mock(HealthPageRenderer.class);
        when(renderer.getJvmStatus()).thenReturn(new HealthPageRenderer.JvmStatus(
                "17.0.1", "OpenJDK", "1 天", 50_000_000L, 200_000_000L, 30_000_000L, 20, 4
        ));
        when(renderer.renderPublic(anyString(), any())).thenReturn("<html>public</html>");

        HealthPageService service = new HealthPageService(dbStatusService, permissionService, renderer);

        String result = service.render();

        assertThat(result).isEqualTo("<html>public</html>");
        verifyNoInteractions(dbStatusService);
    }

    @Test
    void renderShouldReturnDetailedPageWhenAuthenticatedWithPermission() {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "admin", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())
        );

        DatabaseStatusService dbStatusService = mock(DatabaseStatusService.class);
        DatabaseStatusResponse.PostgresStatus pgStatus = new DatabaseStatusResponse.PostgresStatus(
                "localhost", 5432, "leo", "15.0", 10, 5, 100, "50MB", 20, null, "UP"
        );
        DatabaseStatusResponse.RedisStatus redisStatus = new DatabaseStatusResponse.RedisStatus(
                "localhost", 6379, 0, "7.0", 10_000_000L, 50_000_000L, 1000, 5, "1 天", 500, 10, 98.0, "UP"
        );
        when(dbStatusService.getStatus()).thenReturn(new DatabaseStatusResponse(pgStatus, redisStatus));

        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.can(1L, "database", "read")).thenReturn(true);

        HealthPageRenderer renderer = mock(HealthPageRenderer.class);
        when(renderer.getJvmStatus()).thenReturn(new HealthPageRenderer.JvmStatus(
                "17.0.1", "OpenJDK", "1 天", 50_000_000L, 200_000_000L, 30_000_000L, 20, 4
        ));
        when(renderer.renderDebug(anyString(), any(), any(), any())).thenReturn("debug");
        when(renderer.renderDetailed(anyString(), any(), any(), any(), anyString())).thenReturn("<html>detailed</html>");

        HealthPageService service = new HealthPageService(dbStatusService, permissionService, renderer);

        String result = service.render();

        assertThat(result).isEqualTo("<html>detailed</html>");
        verify(dbStatusService).getStatus();
    }

    @Test
    void renderShouldReturnPublicPageWhenAuthenticatedPrincipalHasNoPermission() {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(2L, "auditor", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())
        );

        DatabaseStatusService dbStatusService = mock(DatabaseStatusService.class);
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.can(2L, "database", "read")).thenReturn(false);
        HealthPageRenderer renderer = mock(HealthPageRenderer.class);
        when(renderer.getJvmStatus()).thenReturn(new HealthPageRenderer.JvmStatus(
                "17.0.1", "OpenJDK", "1 天", 50_000_000L, 200_000_000L, 30_000_000L, 20, 4
        ));
        when(renderer.renderPublic(anyString(), any())).thenReturn("<html>public</html>");

        HealthPageService service = new HealthPageService(dbStatusService, permissionService, renderer);

        String result = service.render();

        assertThat(result).isEqualTo("<html>public</html>");
        verify(permissionService).can(2L, "database", "read");
        verifyNoInteractions(dbStatusService);
    }

    @Test
    void renderShouldReturnPublicPageWhenAuthenticatedPrincipalTypeIsUnsupported() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "", List.of())
        );

        DatabaseStatusService dbStatusService = mock(DatabaseStatusService.class);
        PermissionService permissionService = mock(PermissionService.class);
        HealthPageRenderer renderer = mock(HealthPageRenderer.class);
        when(renderer.getJvmStatus()).thenReturn(new HealthPageRenderer.JvmStatus(
                "17.0.1", "OpenJDK", "1 天", 50_000_000L, 200_000_000L, 30_000_000L, 20, 4
        ));
        when(renderer.renderPublic(anyString(), any())).thenReturn("<html>public</html>");

        HealthPageService service = new HealthPageService(dbStatusService, permissionService, renderer);

        String result = service.render();

        assertThat(result).isEqualTo("<html>public</html>");
        verifyNoInteractions(permissionService, dbStatusService);
    }

    @Test
    void renderShouldReturnPublicPageWhenAuthenticationIsNotAuthenticated() {
        var authentication = mock(org.springframework.security.core.Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        DatabaseStatusService dbStatusService = mock(DatabaseStatusService.class);
        PermissionService permissionService = mock(PermissionService.class);
        HealthPageRenderer renderer = mock(HealthPageRenderer.class);
        when(renderer.getJvmStatus()).thenReturn(new HealthPageRenderer.JvmStatus(
                "17.0.1", "OpenJDK", "1 天", 50_000_000L, 200_000_000L, 30_000_000L, 20, 4
        ));
        when(renderer.renderPublic(anyString(), any())).thenReturn("<html>public</html>");

        HealthPageService service = new HealthPageService(dbStatusService, permissionService, renderer);

        String result = service.render();

        assertThat(result).isEqualTo("<html>public</html>");
        verifyNoInteractions(permissionService, dbStatusService);
    }
}
