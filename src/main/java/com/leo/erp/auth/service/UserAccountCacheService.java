package com.leo.erp.auth.service;

import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Service
public class UserAccountCacheService {

    private static final String LOGIN_NAME_OWNER_CACHE_PREFIX = "auth:user:login-name:owner:";
    private static final Duration LOGIN_NAME_OWNER_CACHE_TTL = Duration.ofMinutes(10);

    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final AuthenticatedUserCacheService authenticatedUserCacheService;
    private final DashboardSummaryService dashboardSummaryService;
    private final PermissionService permissionService;

    public UserAccountCacheService(
            @Nullable RedisJsonCacheSupport redisJsonCacheSupport,
            @Nullable AuthenticatedUserCacheService authenticatedUserCacheService,
            @Nullable DashboardSummaryService dashboardSummaryService,
            PermissionService permissionService) {
        this.redisJsonCacheSupport = redisJsonCacheSupport;
        this.authenticatedUserCacheService = authenticatedUserCacheService;
        this.dashboardSummaryService = dashboardSummaryService;
        this.permissionService = permissionService;
    }

    public void evictLoginNameCache(String... loginNames) {
        if (redisJsonCacheSupport == null || loginNames == null || loginNames.length == 0) {
            return;
        }
        List<String> keys = Arrays.stream(loginNames)
                .map(this::normalizeOptionalValue)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .map(this::loginNameOwnerCacheKey)
                .toList();
        if (!keys.isEmpty()) {
            redisJsonCacheSupport.delete(keys);
        }
    }

    public void evictDashboard(Long userId) {
        if (dashboardSummaryService != null) {
            dashboardSummaryService.evictCache(userId);
        }
    }

    public void evictAuthenticatedUser(Long userId) {
        if (authenticatedUserCacheService != null) {
            authenticatedUserCacheService.evict(userId);
        }
    }

    public void evictDepartmentUserCaches(Long previousDepartmentId, Long nextDepartmentId) {
        permissionService.evictDepartmentUserCache(previousDepartmentId);
        if (!java.util.Objects.equals(previousDepartmentId, nextDepartmentId)) {
            permissionService.evictDepartmentUserCache(nextDepartmentId);
        }
    }

    public void evictPermissionCache(Long userId) {
        permissionService.evictCache(userId);
    }

    private String loginNameOwnerCacheKey(String loginName) {
        return LOGIN_NAME_OWNER_CACHE_PREFIX + loginName;
    }

    private String normalizeOptionalValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
