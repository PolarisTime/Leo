package com.leo.erp.system.runtimeconfig.service;

import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.RedisCacheHealthCheck;
import com.leo.erp.common.web.PageQuerySettings;
import com.leo.erp.system.runtimeconfig.feature.FeatureFlagService;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeBusinessConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeConfigResponse;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeFeatureConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeStatementConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeUiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RuntimeConfigService implements RedisCacheHealthCheck {

    public static final String WEIGHT_ONLY_PURCHASE_INBOUNDS_FEATURE = "purchase-inbound.weight-only-view";
    public static final String WEIGHT_ONLY_SALES_OUTBOUNDS_FEATURE = "sales-outbound.weight-only-view";
    public static final String SHOW_SNOWFLAKE_ID_FEATURE = "ui.show-snowflake-id";
    public static final String RUNTIME_CONFIG_CACHE_KEY = "leo:system:runtime-config:v3";
    private static final Duration RUNTIME_CONFIG_CACHE_TTL = Duration.ofMinutes(10);

    private final FeatureFlagService featureFlagService;
    private final PageQuerySettings pageQuerySettings;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    @Autowired
    public RuntimeConfigService(FeatureFlagService featureFlagService,
                                PageQuerySettings pageQuerySettings,
                                @Nullable RedisJsonCacheSupport redisJsonCacheSupport) {
        this.featureFlagService = featureFlagService;
        this.pageQuerySettings = pageQuerySettings;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
    }

    public RuntimeConfigResponse getRuntimeConfig() {
        if (redisJsonCacheSupport != null) {
            var cached = redisJsonCacheSupport.read(RUNTIME_CONFIG_CACHE_KEY, RuntimeConfigResponse.class);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        RuntimeConfigResponse response = loadRuntimeConfig();
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.write(RUNTIME_CONFIG_CACHE_KEY, response, RUNTIME_CONFIG_CACHE_TTL);
        }
        return response;
    }

    private RuntimeConfigResponse loadRuntimeConfig() {
        return new RuntimeConfigResponse(
                uiConfig(),
                businessConfig(),
                featureConfig()
        );
    }

    @Override
    public String cacheName() {
        return RUNTIME_CONFIG_CACHE_KEY;
    }

    @Override
    public CacheHealthCheckResult verifyAndRefreshCache() {
        return verifyAndRefreshValueCache(
                redisJsonCacheSupport,
                RUNTIME_CONFIG_CACHE_KEY,
                RUNTIME_CONFIG_CACHE_TTL,
                RuntimeConfigResponse.class,
                loadRuntimeConfig()
        );
    }

    public void evictCache() {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.delete(RUNTIME_CONFIG_CACHE_KEY);
        }
    }

    private RuntimeUiConfig uiConfig() {
        return new RuntimeUiConfig(
                pageQuerySettings.getDefaultListPageSize(),
                featureFlagService.isEnabled(SHOW_SNOWFLAKE_ID_FEATURE, false)
        );
    }

    private RuntimeBusinessConfig businessConfig() {
        RuntimeStatementConfig statement = new RuntimeStatementConfig(true);
        return new RuntimeBusinessConfig(statement);
    }

    private RuntimeFeatureConfig featureConfig() {
        return new RuntimeFeatureConfig(
                featureFlagService.isEnabled(WEIGHT_ONLY_PURCHASE_INBOUNDS_FEATURE, false),
                featureFlagService.isEnabled(WEIGHT_ONLY_SALES_OUTBOUNDS_FEATURE, false)
        );
    }
}
