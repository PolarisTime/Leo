package com.leo.erp.system.runtimeconfig.service;

import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.RedisCacheHealthCheck;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.runtimeconfig.feature.FeatureFlagService;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeBusinessConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeBusinessNoConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeConfigResponse;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeFeatureConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeStatementConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeUiConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeWatermarkConfig;
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
    public static final String UI_WATERMARK_ENABLED_FEATURE = "ui.watermark.enabled";
    public static final String RUNTIME_CONFIG_CACHE_KEY = "leo:system:runtime-config";
    public static final String WATERMARK_CONTENT_SETTING = "SYS_WATERMARK_CONTENT";
    public static final String WATERMARK_FONT_SIZE_SETTING = "SYS_WATERMARK_FONT_SIZE";
    public static final String WATERMARK_ROTATE_SETTING = "SYS_WATERMARK_ROTATE";
    public static final String WATERMARK_COLOR_SETTING = "SYS_WATERMARK_COLOR";
    public static final String WATERMARK_DENSITY_SETTING = "SYS_WATERMARK_DENSITY";
    private static final Duration RUNTIME_CONFIG_CACHE_TTL = Duration.ofMinutes(10);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 200;
    private static final String DEFAULT_WATERMARK_CONTENT = "{username}  {time}";
    private static final int DEFAULT_WATERMARK_FONT_SIZE = 18;
    private static final int DEFAULT_WATERMARK_ROTATE = -22;
    private static final String DEFAULT_WATERMARK_COLOR = "rgba(0,0,0,0.08)";
    private static final int DEFAULT_WATERMARK_DENSITY = 200;
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.13");

    private static final Set<String> RUNTIME_SETTING_CODES = Set.of(
            SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING,
            SystemSwitchService.SHOW_SNOWFLAKE_ID_SWITCH,
            SystemSwitchService.UI_WATERMARK_ENABLED_SWITCH,
            WATERMARK_CONTENT_SETTING,
            WATERMARK_FONT_SIZE_SETTING,
            WATERMARK_ROTATE_SETTING,
            WATERMARK_COLOR_SETTING,
            WATERMARK_DENSITY_SETTING,
            CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE,
            SystemSwitchService.CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH,
            SystemSwitchService.USE_SNOWFLAKE_ID_AS_BUSINESS_NO_SWITCH,
            WEIGHT_ONLY_PURCHASE_INBOUNDS_SETTING,
            WEIGHT_ONLY_SALES_OUTBOUNDS_SETTING
    );

    private final NoRuleRepository noRuleRepository;
    private final FeatureFlagService featureFlagService;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    public RuntimeConfigService(NoRuleRepository noRuleRepository, FeatureFlagService featureFlagService) {
        this(noRuleRepository, featureFlagService, null);
    }

    @Autowired
    public RuntimeConfigService(NoRuleRepository noRuleRepository,
                                FeatureFlagService featureFlagService,
                                @Nullable RedisJsonCacheSupport redisJsonCacheSupport) {
        this.noRuleRepository = noRuleRepository;
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
        Map<String, NoRule> settings = loadRuntimeSettings();
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

    private Map<String, NoRule> loadRuntimeSettings() {
        return noRuleRepository.findBySettingCodeInAndDeletedFlagFalse(RUNTIME_SETTING_CODES).stream()
                .filter(rule -> rule.getSettingCode() != null)
                .collect(Collectors.toMap(
                        rule -> rule.getSettingCode().trim(),
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private RuntimeUiConfig uiConfig(Map<String, NoRule> settings) {
        RuntimeWatermarkConfig watermark = new RuntimeWatermarkConfig(
                featureFlag(UI_WATERMARK_ENABLED_FEATURE, settings, SystemSwitchService.UI_WATERMARK_ENABLED_SWITCH),
                text(settings, WATERMARK_CONTENT_SETTING, DEFAULT_WATERMARK_CONTENT),
                integer(settings, WATERMARK_FONT_SIZE_SETTING, DEFAULT_WATERMARK_FONT_SIZE, 8, 72),
                text(settings, WATERMARK_COLOR_SETTING, DEFAULT_WATERMARK_COLOR),
                integer(settings, WATERMARK_ROTATE_SETTING, DEFAULT_WATERMARK_ROTATE, -90, 90),
                integer(settings, WATERMARK_DENSITY_SETTING, DEFAULT_WATERMARK_DENSITY, 80, 600)
        );
        return new RuntimeUiConfig(
                integer(settings, SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING, DEFAULT_PAGE_SIZE, MIN_PAGE_SIZE, MAX_PAGE_SIZE),
                featureFlag(SHOW_SNOWFLAKE_ID_FEATURE, settings, SystemSwitchService.SHOW_SNOWFLAKE_ID_SWITCH),
                watermark
        );
    }

    private RuntimeBusinessConfig businessConfig(Map<String, NoRule> settings) {
        RuntimeStatementConfig statement = new RuntimeStatementConfig(
                enabled(settings, SystemSwitchService.CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH)
        );
        RuntimeBusinessNoConfig businessNo = new RuntimeBusinessNoConfig(
                enabled(settings, SystemSwitchService.USE_SNOWFLAKE_ID_AS_BUSINESS_NO_SWITCH)
        );
        return new RuntimeBusinessConfig(
                decimal(settings, CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE, DEFAULT_TAX_RATE),
                statement,
                businessNo
        );
    }

    private RuntimeFeatureConfig featureConfig(Map<String, NoRule> settings) {
        return new RuntimeFeatureConfig(
                featureFlag(WEIGHT_ONLY_PURCHASE_INBOUNDS_FEATURE, settings, WEIGHT_ONLY_PURCHASE_INBOUNDS_SETTING),
                featureFlag(WEIGHT_ONLY_SALES_OUTBOUNDS_FEATURE, settings, WEIGHT_ONLY_SALES_OUTBOUNDS_SETTING)
        );
    }

    private boolean featureFlag(String key, Map<String, NoRule> settings, String fallbackSettingCode) {
        return featureFlagService.isEnabled(key, enabled(settings, fallbackSettingCode));
    }

    private boolean enabled(Map<String, NoRule> settings, String code) {
        NoRule rule = settings.get(code);
        return rule != null && "正常".equals(rule.getStatus());
    }

    private String text(Map<String, NoRule> settings, String code, String fallback) {
        NoRule rule = activeSetting(settings, code);
        if (rule == null || rule.getSampleNo() == null || rule.getSampleNo().isBlank()) {
            return fallback;
        }
        return rule.getSampleNo().trim();
    }

    private int integer(Map<String, NoRule> settings, String code, int fallback, int min, int max) {
        NoRule rule = activeSetting(settings, code);
        if (rule == null) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(rule.getSampleNo() == null ? "" : rule.getSampleNo().trim());
            return value >= min && value <= max ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private BigDecimal decimal(Map<String, NoRule> settings, String code, BigDecimal fallback) {
        NoRule rule = activeSetting(settings, code);
        if (rule == null) {
            return fallback;
        }
        try {
            BigDecimal value = new BigDecimal(rule.getSampleNo() == null ? "" : rule.getSampleNo().trim());
            return value.signum() >= 0 ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private NoRule activeSetting(Map<String, NoRule> settings, String code) {
        NoRule rule = settings.get(code);
        return rule != null && "正常".equals(rule.getStatus()) ? rule : null;
    }

    static Collection<String> runtimeSettingCodes() {
        return RUNTIME_SETTING_CODES;
    }
}
