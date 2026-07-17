package com.leo.erp.system.runtimeconfig.service;

import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.RedisCacheHealthCheck;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.generalsetting.domain.entity.GeneralSetting;
import com.leo.erp.system.generalsetting.repository.GeneralSettingRepository;
import com.leo.erp.system.generalsetting.service.SystemSwitchService;
import com.leo.erp.system.runtimeconfig.feature.FeatureFlagService;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeBusinessConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeConfigResponse;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeFeatureConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeStatementConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeUiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RuntimeConfigService implements RedisCacheHealthCheck {

    public static final String WEIGHT_ONLY_PURCHASE_INBOUNDS_SETTING = "UI_WEIGHT_ONLY_PURCHASE_INBOUNDS";
    public static final String WEIGHT_ONLY_SALES_OUTBOUNDS_SETTING = "UI_WEIGHT_ONLY_SALES_OUTBOUNDS";
    public static final String WEIGHT_ONLY_PURCHASE_INBOUNDS_FEATURE = "purchase-inbound.weight-only-view";
    public static final String WEIGHT_ONLY_SALES_OUTBOUNDS_FEATURE = "sales-outbound.weight-only-view";
    public static final String SHOW_SNOWFLAKE_ID_FEATURE = "ui.show-snowflake-id";
    public static final String RUNTIME_CONFIG_CACHE_KEY = "leo:system:runtime-config";
    private static final Duration RUNTIME_CONFIG_CACHE_TTL = Duration.ofMinutes(10);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 200;
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.13");

    private static final Set<String> RUNTIME_SETTING_CODES = Set.of(
            SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING,
            SystemSwitchService.SHOW_SNOWFLAKE_ID_SWITCH,
            CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE,
            SystemSwitchService.CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH,
            WEIGHT_ONLY_PURCHASE_INBOUNDS_SETTING,
            WEIGHT_ONLY_SALES_OUTBOUNDS_SETTING
    );

    private final GeneralSettingRepository generalSettingRepository;
    private final FeatureFlagService featureFlagService;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    public RuntimeConfigService(
            GeneralSettingRepository generalSettingRepository,
            FeatureFlagService featureFlagService
    ) {
        this(generalSettingRepository, featureFlagService, null);
    }

    @Autowired
    public RuntimeConfigService(GeneralSettingRepository generalSettingRepository,
                                FeatureFlagService featureFlagService,
                                @Nullable RedisJsonCacheSupport redisJsonCacheSupport) {
        this.generalSettingRepository = generalSettingRepository;
        this.featureFlagService = featureFlagService;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
    }

    @Transactional(readOnly = true)
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
        Map<String, GeneralSetting> settings = loadRuntimeSettings();
        return new RuntimeConfigResponse(
                uiConfig(settings),
                businessConfig(settings),
                featureConfig(settings)
        );
    }

    @Override
    public String cacheName() {
        return RUNTIME_CONFIG_CACHE_KEY;
    }

    @Override
    @Transactional(readOnly = true)
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

    private Map<String, GeneralSetting> loadRuntimeSettings() {
        return generalSettingRepository.findBySettingCodeInAndDeletedFlagFalse(RUNTIME_SETTING_CODES).stream()
                .filter(rule -> rule.getSettingCode() != null)
                .collect(Collectors.toMap(
                        rule -> rule.getSettingCode().trim(),
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private RuntimeUiConfig uiConfig(Map<String, GeneralSetting> settings) {
        return new RuntimeUiConfig(
                integer(
                        settings,
                        SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING,
                        DEFAULT_PAGE_SIZE,
                        MIN_PAGE_SIZE,
                        MAX_PAGE_SIZE
                ),
                featureFlag(SHOW_SNOWFLAKE_ID_FEATURE, settings, SystemSwitchService.SHOW_SNOWFLAKE_ID_SWITCH)
        );
    }

    private RuntimeBusinessConfig businessConfig(Map<String, GeneralSetting> settings) {
        RuntimeStatementConfig statement = new RuntimeStatementConfig(
                enabled(settings, SystemSwitchService.CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH)
        );
        return new RuntimeBusinessConfig(
                decimal(settings, CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE, DEFAULT_TAX_RATE),
                statement
        );
    }

    private RuntimeFeatureConfig featureConfig(Map<String, GeneralSetting> settings) {
        return new RuntimeFeatureConfig(
                featureFlag(WEIGHT_ONLY_PURCHASE_INBOUNDS_FEATURE, settings, WEIGHT_ONLY_PURCHASE_INBOUNDS_SETTING),
                featureFlag(WEIGHT_ONLY_SALES_OUTBOUNDS_FEATURE, settings, WEIGHT_ONLY_SALES_OUTBOUNDS_SETTING)
        );
    }

    private boolean featureFlag(String key, Map<String, GeneralSetting> settings, String fallbackSettingCode) {
        return featureFlagService.isEnabled(key, enabled(settings, fallbackSettingCode));
    }

    private boolean enabled(Map<String, GeneralSetting> settings, String code) {
        GeneralSetting rule = settings.get(code);
        return rule != null && "正常".equals(rule.getStatus());
    }

    private int integer(Map<String, GeneralSetting> settings, String code, int fallback, int min, int max) {
        GeneralSetting rule = activeSetting(settings, code);
        if (rule == null) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(rule.getSettingValue() == null ? "" : rule.getSettingValue().trim());
            return value >= min && value <= max ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private BigDecimal decimal(Map<String, GeneralSetting> settings, String code, BigDecimal fallback) {
        GeneralSetting rule = activeSetting(settings, code);
        if (rule == null) {
            return fallback;
        }
        try {
            BigDecimal value = new BigDecimal(rule.getSettingValue() == null ? "" : rule.getSettingValue().trim());
            return value.signum() >= 0 ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private GeneralSetting activeSetting(Map<String, GeneralSetting> settings, String code) {
        GeneralSetting rule = settings.get(code);
        return rule != null && "正常".equals(rule.getStatus()) ? rule : null;
    }

    static Collection<String> runtimeSettingCodes() {
        return RUNTIME_SETTING_CODES;
    }
}
