package com.leo.erp.auth.service;

import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserAccountCacheServiceTest {

    @Test
    void shouldEvictLoginNameCache_whenLoginNamesProvided() {
        var redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        var service = new UserAccountCacheService(redisJsonCacheSupport, null, null, null);

        service.evictLoginNameCache("admin", "user1");
    }

    @Test
    void shouldNotEvictLoginNameCache_whenRedisNull() {
        var service = new UserAccountCacheService(null, null, null, null);

        service.evictLoginNameCache("admin");
    }

    @Test
    void shouldNotEvictLoginNameCache_whenLoginNamesEmpty() {
        var redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        var service = new UserAccountCacheService(redisJsonCacheSupport, null, null, null);

        service.evictLoginNameCache();
    }

    @Test
    void shouldNotEvictLoginNameCache_whenLoginNamesArrayNull() {
        var redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        var service = new UserAccountCacheService(redisJsonCacheSupport, null, null, null);

        service.evictLoginNameCache((String[]) null);
    }

    @Test
    void shouldNotEvictLoginNameCache_whenLoginNamesBlank() {
        var redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        var service = new UserAccountCacheService(redisJsonCacheSupport, null, null, null);

        service.evictLoginNameCache("", "  ");
    }

    @Test
    void shouldEvictDashboard_whenServiceAvailable() {
        var dashboardSummaryService = mock(DashboardSummaryService.class);
        var service = new UserAccountCacheService(null, null, dashboardSummaryService, null);

        service.evictDashboard(1L);
    }

    @Test
    void shouldNotEvictDashboard_whenServiceNull() {
        var service = new UserAccountCacheService(null, null, null, null);

        service.evictDashboard(1L);
    }

    @Test
    void shouldEvictAuthenticatedUser_whenServiceAvailable() {
        var authenticatedUserCacheService = mock(AuthenticatedUserCacheService.class);
        var service = new UserAccountCacheService(null, authenticatedUserCacheService, null, null);

        service.evictAuthenticatedUser(1L);
    }

    @Test
    void shouldNotEvictAuthenticatedUser_whenServiceNull() {
        var service = new UserAccountCacheService(null, null, null, null);

        service.evictAuthenticatedUser(1L);
    }

    @Test
    void shouldEvictDepartmentUserCaches() {
        var permissionService = mock(PermissionService.class);
        var service = new UserAccountCacheService(null, null, null, permissionService);

        service.evictDepartmentUserCaches(1L, 2L);

        verify(permissionService).clearDepartmentUserCache();
    }

    @Test
    void shouldEvictDepartmentUserCaches_whenSameDepartment() {
        var permissionService = mock(PermissionService.class);
        var service = new UserAccountCacheService(null, null, null, permissionService);

        service.evictDepartmentUserCaches(1L, 1L);

        verify(permissionService).clearDepartmentUserCache();
    }

    @Test
    void shouldEvictPermissionCache() {
        var permissionService = mock(PermissionService.class);
        var service = new UserAccountCacheService(null, null, null, permissionService);

        service.evictPermissionCache(1L);
    }
}
