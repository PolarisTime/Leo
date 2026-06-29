package com.leo.erp.system.schedule.service;

import com.leo.erp.common.support.RedisCacheHealthCheck;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RedisCacheHealthCheckService {

    private final List<RedisCacheHealthCheck> healthChecks;

    public RedisCacheHealthCheckService(List<RedisCacheHealthCheck> healthChecks) {
        this.healthChecks = healthChecks == null ? List.of() : List.copyOf(healthChecks);
    }

    public void verifyAndRefreshCaches() {
        for (RedisCacheHealthCheck healthCheck : healthChecks) {
            verifyOne(healthCheck);
        }
    }

    private void verifyOne(RedisCacheHealthCheck healthCheck) {
        String cacheName = healthCheck.cacheName();
        try {
            RedisCacheHealthCheck.CacheHealthCheckResult result = healthCheck.verifyAndRefreshCache();
            if (result == null) {
                log.warn("Redis 缓存巡检返回空结果 key={}", cacheName);
                return;
            }
            if (result.refreshed()) {
                log.warn(
                        "Redis 缓存巡检已刷新 key={}, currentSize={}, refreshedSize={}",
                        result.cacheName(),
                        result.currentSize(),
                        result.refreshedSize()
                );
            }
        } catch (Exception ex) {
            log.warn("Redis 缓存巡检失败 key={}", cacheName, ex);
        }
    }
}
