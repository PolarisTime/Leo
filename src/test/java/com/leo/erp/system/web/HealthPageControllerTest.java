package com.leo.erp.system.web;

import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.database.service.DatabaseStatusService;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HealthPageControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldHideInfrastructureDetailsFromAnonymousRequests() {
        StubDatabaseStatusService databaseStatusService = new StubDatabaseStatusService(null);
        HealthPageController controller = new HealthPageController(databaseStatusService, new StubPermissionService(false));

        String html = controller.health();

        assertThat(html).contains("公开页仅展示基础可用性");
        assertThat(html).doesNotContain("db.internal");
        assertThat(html).doesNotContain("Debug 输出");
        assertThat(databaseStatusService.called).isFalse();
    }

    @Test
    void shouldHideInfrastructureDetailsFromAuthenticatedRequestsWithoutPermission() {
        StubDatabaseStatusService databaseStatusService = new StubDatabaseStatusService(null);
        SecurityPrincipal principal = new SecurityPrincipal(
                2L,
                "user",
                "encoded",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        HealthPageController controller = new HealthPageController(databaseStatusService, new StubPermissionService(false));

        String html = controller.health();

        assertThat(html).contains("公开页仅展示基础可用性");
        assertThat(html).doesNotContain("Debug 输出");
        assertThat(databaseStatusService.called).isFalse();
    }

    @Test
    void shouldShowDetailedStatusForAuthorizedRequests() {
        StubDatabaseStatusService databaseStatusService = new StubDatabaseStatusService(new DatabaseStatusResponse(
                new DatabaseStatusResponse.PostgresStatus(
                        "db.internal",
                        5432,
                        "leo",
                        "PostgreSQL 18",
                        5,
                        1,
                        100,
                        "32 MB",
                        42,
                        LocalDateTime.of(2026, 4, 25, 9, 0),
                        "正常"
                ),
                new DatabaseStatusResponse.RedisStatus(
                        "redis.internal",
                        16379,
                        3,
                        "7.2.0",
                        1024,
                        2048,
                        12,
                        3,
                        "1 天 2 小时",
                        8,
                        2,
                        80.0,
                        "正常"
                )
        ));
        SecurityPrincipal principal = new SecurityPrincipal(
                1L,
                "admin",
                "encoded",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        HealthPageController controller = new HealthPageController(databaseStatusService, new StubPermissionService(true));

        String html = controller.health();

        assertThat(html).contains("db.internal:5432");
        assertThat(html).contains("redis.internal:16379 / DB 3");
        assertThat(html).contains("Debug 输出");
        assertThat(databaseStatusService.called).isTrue();
    }

    private static final class StubDatabaseStatusService extends DatabaseStatusService {

        private final DatabaseStatusResponse response;
        private boolean called;

        private StubDatabaseStatusService(DatabaseStatusResponse response) {
            super(null, null);
            this.response = response;
        }

        @Override
        public DatabaseStatusResponse getStatus() {
            called = true;
            return response;
        }
    }

    private static final class StubPermissionService extends PermissionService {

        private final boolean allowed;

        private StubPermissionService(boolean allowed) {
            super(null, null, null, null, null, null);
            this.allowed = allowed;
        }

        @Override
        public boolean can(Long userId, String resourceCode, String actionCode) {
            return allowed;
        }
    }
}
