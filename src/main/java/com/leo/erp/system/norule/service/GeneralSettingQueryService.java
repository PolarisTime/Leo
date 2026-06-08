package com.leo.erp.system.norule.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.setting.PageUploadRuleQueryService;
import com.leo.erp.common.setting.PageUploadRuleSummary;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.norule.mapper.NoRuleMapper;
import com.leo.erp.system.norule.web.dto.GeneralSettingResponse;
import com.leo.erp.system.norule.web.dto.NoRuleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GeneralSettingQueryService {

    public static final String PUBLIC_DISPLAY_SWITCHES_CACHE_KEY = "leo:system:public-display-switches";
    private static final Duration PUBLIC_DISPLAY_SWITCHES_CACHE_TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<GeneralSettingResponse>> PUBLIC_DISPLAY_SWITCH_LIST_TYPE = new TypeReference<>() { };
    public static final String PUBLIC_CLIENT_SETTINGS_CACHE_KEY = "leo:system:public-client-settings";
    private static final Duration PUBLIC_CLIENT_SETTINGS_CACHE_TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<GeneralSettingResponse>> PUBLIC_CLIENT_SETTING_LIST_TYPE = new TypeReference<>() { };

    private static final Map<String, Integer> GENERAL_SETTING_ORDER = Map.ofEntries(
            Map.entry("RULE_MAT", 10),
            Map.entry("RULE_MC", 20),
            Map.entry("RULE_SUP", 30),
            Map.entry("RULE_CUST", 40),
            Map.entry("RULE_CAR", 50),
            Map.entry("RULE_WH", 60),
            Map.entry("RULE_PO", 70),
            Map.entry("RULE_PI", 80),
            Map.entry("RULE_SO", 90),
            Map.entry("RULE_OB", 100),
            Map.entry("RULE_FB", 110),
            Map.entry("RULE_PC", 120),
            Map.entry("RULE_SC", 130),
            Map.entry("RULE_SS", 140),
            Map.entry("RULE_CS", 150),
            Map.entry("RULE_FS", 160),
            Map.entry("RULE_RC", 170),
            Map.entry("RULE_PM", 180),
            Map.entry("RULE_SP", 190),
            Map.entry("RULE_KP", 200),
            Map.entry("RULE_BATCH_NO", 210),
            Map.entry(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE, 95),
            Map.entry(SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING, 98),
            Map.entry("UI_WEIGHT_ONLY_PURCHASE_INBOUNDS", 100),
            Map.entry("UI_WEIGHT_ONLY_SALES_OUTBOUNDS", 110),
            Map.entry(SystemSwitchService.CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH, 120),
            Map.entry(SystemSwitchService.SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE_SWITCH, 130),
            Map.entry("SYS_OPERATION_LOG_RECORD_ALL_WRITE", 140),
            Map.entry("SYS_OPERATION_LOG_DETAILED_PAGE_ACTIONS", 150),
            Map.entry("SYS_OPERATION_LOG_RECORD_AUTH_EVENTS", 160),
            Map.entry("SYS_FORCE_USER_TOTP_ON_FIRST_LOGIN", 170),
            Map.entry("SYS_BATCH_NO_AUTO_GENERATE", 180),
            Map.entry("UI_HIDE_AUDITED_LIST_RECORDS", 190),
            Map.entry("SYS_ADMIN_VIEW_DELETED_RECORDS", 195),
            Map.entry("UI_SHOW_SNOWFLAKE_ID", 200),
            Map.entry("SYS_LOGIN_CAPTCHA", 205),
            Map.entry(SystemSwitchService.USE_SNOWFLAKE_ID_AS_BUSINESS_NO_SWITCH, 210),
            Map.entry(SystemSwitchService.UI_WATERMARK_ENABLED_SWITCH, 220),
            Map.entry("SYS_WATERMARK_CONTENT", 221),
            Map.entry("SYS_WATERMARK_FONT_SIZE", 222),
            Map.entry("SYS_WATERMARK_ROTATE", 223),
            Map.entry("SYS_WATERMARK_COLOR", 224),
            Map.entry("SYS_WATERMARK_DENSITY", 225),
            Map.entry("PAGE_UPLOAD", 900)
    );

    private static final Set<String> PUBLIC_DISPLAY_SWITCH_CODES = Set.of(
            "UI_WEIGHT_ONLY_PURCHASE_INBOUNDS",
            "UI_WEIGHT_ONLY_SALES_OUTBOUNDS",
            SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH,
            SystemSwitchService.SHOW_SNOWFLAKE_ID_SWITCH
    );
    private static final Set<String> PUBLIC_CLIENT_SETTING_CODES = Set.of(
            "UI_WEIGHT_ONLY_PURCHASE_INBOUNDS",
            "UI_WEIGHT_ONLY_SALES_OUTBOUNDS",
            CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE,
            SystemSwitchService.CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH,
            SystemSwitchService.SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE_SWITCH,
            SystemSwitchService.SHOW_SNOWFLAKE_ID_SWITCH,
            SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING,
            SystemSwitchService.USE_SNOWFLAKE_ID_AS_BUSINESS_NO_SWITCH,
            SystemSwitchService.UI_WATERMARK_ENABLED_SWITCH,
            "SYS_WATERMARK_CONTENT",
            "SYS_WATERMARK_FONT_SIZE",
            "SYS_WATERMARK_ROTATE",
            "SYS_WATERMARK_COLOR",
            "SYS_WATERMARK_DENSITY"
    );

    private final NoRuleRepository noRuleRepository;
    private final NoRuleMapper noRuleMapper;
    private final PageUploadRuleQueryService pageUploadRuleQueryService;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    @Autowired
    public GeneralSettingQueryService(NoRuleRepository noRuleRepository,
                                      NoRuleMapper noRuleMapper,
                                      PageUploadRuleQueryService pageUploadRuleQueryService,
                                      RedisJsonCacheSupport redisJsonCacheSupport) {
        this.noRuleRepository = noRuleRepository;
        this.noRuleMapper = noRuleMapper;
        this.pageUploadRuleQueryService = pageUploadRuleQueryService;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
    }

    public GeneralSettingQueryService(NoRuleRepository noRuleRepository,
                                      NoRuleMapper noRuleMapper,
                                      PageUploadRuleQueryService pageUploadRuleQueryService) {
        this(noRuleRepository, noRuleMapper, pageUploadRuleQueryService, null);
    }

    @Transactional(readOnly = true)
    public Page<GeneralSettingResponse> page(PageQuery query, String keyword, String status) {
        Specification<com.leo.erp.system.norule.domain.entity.NoRule> spec = Specs
                .<com.leo.erp.system.norule.domain.entity.NoRule>notDeleted()
                .and(Specs.keywordLike(keyword, "settingCode", "settingName", "billName"))
                .and(Specs.equalIfPresent("status", status));
        List<GeneralSettingResponse> merged = new ArrayList<>();
        noRuleRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(noRuleMapper::toResponse)
                .map(this::toGeneralSettingResponse)
                .forEach(merged::add);
        pageUploadRuleQueryService.listPageUploadRules().stream()
                .filter(rule -> matchesKeyword(rule, keyword))
                .map(this::toGeneralSettingResponse)
                .forEach(merged::add);
        merged.removeIf(item -> !matchesStatus(item, status));
        merged.sort(generalSettingComparator());

        int start = Math.min(query.page() * query.size(), merged.size());
        int end = Math.min(start + query.size(), merged.size());
        return new PageImpl<>(
                merged.subList(start, end),
                PageRequest.of(query.page(), query.size()),
                merged.size()
        );
    }

    @Transactional(readOnly = true)
    public List<GeneralSettingResponse> publicDisplaySwitches() {
        if (redisJsonCacheSupport == null) {
            return loadPublicDisplaySwitches();
        }
        return redisJsonCacheSupport.getOrLoad(
                PUBLIC_DISPLAY_SWITCHES_CACHE_KEY,
                PUBLIC_DISPLAY_SWITCHES_CACHE_TTL,
                PUBLIC_DISPLAY_SWITCH_LIST_TYPE,
                this::loadPublicDisplaySwitches
        );
    }

    @Transactional(readOnly = true)
    public List<GeneralSettingResponse> publicClientSettings() {
        if (redisJsonCacheSupport == null) {
            return loadPublicClientSettings();
        }
        return redisJsonCacheSupport.getOrLoad(
                PUBLIC_CLIENT_SETTINGS_CACHE_KEY,
                PUBLIC_CLIENT_SETTINGS_CACHE_TTL,
                PUBLIC_CLIENT_SETTING_LIST_TYPE,
                this::loadPublicClientSettings
        );
    }

    public void evictPublicDisplaySwitchesCache() {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.delete(PUBLIC_DISPLAY_SWITCHES_CACHE_KEY);
        }
    }

    private List<GeneralSettingResponse> loadPublicDisplaySwitches() {
        return noRuleRepository.findBySettingCodeInAndDeletedFlagFalse(PUBLIC_DISPLAY_SWITCH_CODES).stream()
                .map(noRuleMapper::toResponse)
                .map(this::toGeneralSettingResponse)
                .sorted(generalSettingComparator())
                .toList();
    }

    private List<GeneralSettingResponse> loadPublicClientSettings() {
        return noRuleRepository.findBySettingCodeInAndDeletedFlagFalse(PUBLIC_CLIENT_SETTING_CODES).stream()
                .map(noRuleMapper::toResponse)
                .map(this::toGeneralSettingResponse)
                .sorted(generalSettingComparator())
                .toList();
    }

    private GeneralSettingResponse toGeneralSettingResponse(NoRuleResponse rule) {
        return new GeneralSettingResponse(
                rule.id(),
                rule.settingCode(),
                rule.settingName(),
                rule.billName(),
                rule.prefix(),
                rule.dateRule(),
                rule.serialLength(),
                rule.resetRule(),
                rule.sampleNo(),
                rule.status(),
                rule.remark(),
                "NO_RULE",
                null
        );
    }

    private GeneralSettingResponse toGeneralSettingResponse(PageUploadRuleSummary rule) {
        return new GeneralSettingResponse(
                rule.id(),
                rule.ruleCode(),
                rule.ruleName(),
                rule.moduleName(),
                rule.renamePattern(),
                null,
                null,
                null,
                rule.previewFileName(),
                rule.status(),
                rule.remark(),
                "UPLOAD_RULE",
                rule.moduleKey()
        );
    }

    private boolean matchesKeyword(PageUploadRuleSummary rule, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase();
        return contains(rule.ruleCode(), normalizedKeyword)
                || contains(rule.ruleName(), normalizedKeyword)
                || contains(rule.moduleName(), normalizedKeyword)
                || contains(rule.renamePattern(), normalizedKeyword)
                || contains(rule.remark(), normalizedKeyword);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword);
    }

    private boolean matchesStatus(GeneralSettingResponse item, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return status.trim().equals(item.status());
    }

    private Comparator<GeneralSettingResponse> generalSettingComparator() {
        return Comparator
                .comparingInt(this::resolveSortOrder)
                .thenComparingInt(item -> "UPLOAD_RULE".equals(item.ruleType()) ? 1 : 0)
                .thenComparing(item -> defaultString(item.billName()))
                .thenComparing(item -> defaultString(item.settingCode()));
    }

    private int resolveSortOrder(GeneralSettingResponse item) {
        return GENERAL_SETTING_ORDER.getOrDefault(item.settingCode(), 500);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
