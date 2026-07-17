package com.leo.erp.system.generalsetting.service;

import com.leo.erp.common.service.CrudRuntimeSettings;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.TradeItemRuntimeSettings;
import com.leo.erp.common.web.PageQuerySettings;
import com.leo.erp.system.generalsetting.repository.GeneralSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SystemSwitchService implements CrudRuntimeSettings, PageQuerySettings, TradeItemRuntimeSettings {

    public static final String FORCE_BATCH_MANAGEMENT_SWITCH = "SYS_FORCE_BATCH_MANAGEMENT";
    public static final String DEFAULT_LIST_PAGE_SIZE_SETTING = "UI_DEFAULT_LIST_PAGE_SIZE";
    public static final String HIDE_AUDITED_LIST_RECORDS_SWITCH = "UI_HIDE_AUDITED_LIST_RECORDS";
    public static final String SHOW_SNOWFLAKE_ID_SWITCH = "UI_SHOW_SNOWFLAKE_ID";
    public static final String CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH =
            "SYS_CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER";
    public static final String OOBE_COMPLETED_SWITCH = "SYS_OOBE_COMPLETED";
    public static final String SWITCH_CACHE_KEY = "leo:system:switches";
    private static final int DEFAULT_LIST_PAGE_SIZE = 20;
    private static final int MAX_LIST_PAGE_SIZE = 200;
    private static final Set<String> KNOWN_SWITCH_CODES = Set.of(
            FORCE_BATCH_MANAGEMENT_SWITCH,
            DEFAULT_LIST_PAGE_SIZE_SETTING,
            HIDE_AUDITED_LIST_RECORDS_SWITCH,
            SHOW_SNOWFLAKE_ID_SWITCH,
            CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH,
            OOBE_COMPLETED_SWITCH
    );

    private final GeneralSettingRepository generalSettingRepository;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    @Autowired
    public SystemSwitchService(
            GeneralSettingRepository generalSettingRepository,
            RedisJsonCacheSupport redisJsonCacheSupport
    ) {
        this.generalSettingRepository = generalSettingRepository;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
    }

    public SystemSwitchService(GeneralSettingRepository generalSettingRepository) {
        this.generalSettingRepository = generalSettingRepository;
        this.redisJsonCacheSupport = null;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String settingCode) {
        return findSwitch(settingCode)
                .map(rule -> "正常".equals(rule.status()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean shouldForceBatchManagement() {
        return isEnabled(FORCE_BATCH_MANAGEMENT_SWITCH);
    }

    @Transactional(readOnly = true)
    public boolean shouldHideAuditedListRecords() {
        return isEnabled(HIDE_AUDITED_LIST_RECORDS_SWITCH);
    }

    @Transactional(readOnly = true)
    public Set<String> getHiddenAuditedStatuses() {
        if (!isEnabled(HIDE_AUDITED_LIST_RECORDS_SWITCH)) {
            return Set.of();
        }
        Set<String> statuses = generalSettingRepository
                .findBySettingCodeAndDeletedFlagFalse(HIDE_AUDITED_LIST_RECORDS_SWITCH)
                .map(rule -> {
                    String settingValue = rule.getSettingValue();
                    if (settingValue == null || settingValue.isBlank() || "ON".equals(settingValue.trim())) {
                        return Set.of(com.leo.erp.common.support.StatusConstants.AUDITED);
                    }
                    if ("OFF".equals(settingValue.trim())) {
                        return Set.<String>of();
                    }
                    return Arrays.stream(settingValue.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(java.util.stream.Collectors.toSet());
                })
                .orElse(Set.of(com.leo.erp.common.support.StatusConstants.AUDITED));
        return statuses;
    }

    @Transactional(readOnly = true)
    public boolean shouldShowSnowflakeId() {
        return isEnabled(SHOW_SNOWFLAKE_ID_SWITCH);
    }

    @Transactional(readOnly = true)
    public boolean shouldDefaultCustomerStatementReceiptAmountToZero() {
        return isEnabled(CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH);
    }

    @Transactional(readOnly = true)
    public boolean isOobeCompleted() {
        return isEnabled(OOBE_COMPLETED_SWITCH);
    }

    @Transactional(readOnly = true)
    public int getDefaultListPageSize() {
        return findSwitch(DEFAULT_LIST_PAGE_SIZE_SETTING)
                .filter(rule -> "正常".equals(rule.status()))
                .map(rule -> parseBoundedPositiveInt(rule.settingValue(), DEFAULT_LIST_PAGE_SIZE))
                .orElse(DEFAULT_LIST_PAGE_SIZE);
    }

    public void evictCache() {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.delete(SWITCH_CACHE_KEY);
        }
    }

    private Optional<SwitchSnapshot> findSwitch(String settingCode) {
        if (settingCode == null || settingCode.isBlank()) {
            return Optional.empty();
        }
        String normalizedCode = settingCode.trim();
        if (KNOWN_SWITCH_CODES.contains(normalizedCode)) {
            return Optional.ofNullable(loadKnownSwitches().get(normalizedCode));
        }
        return generalSettingRepository.findBySettingCodeAndDeletedFlagFalse(normalizedCode)
                .map(rule -> new SwitchSnapshot(rule.getStatus(), rule.getSettingValue()));
    }

    private Map<String, SwitchSnapshot> loadKnownSwitches() {
        return loadKnownSwitchesFromDatabase();
    }

    private Map<String, SwitchSnapshot> loadKnownSwitchesFromDatabase() {
        return generalSettingRepository.findBySettingCodeInAndDeletedFlagFalse(KNOWN_SWITCH_CODES).stream()
                .collect(Collectors.toMap(
                        rule -> rule.getSettingCode().trim(),
                        rule -> new SwitchSnapshot(rule.getStatus(), rule.getSettingValue()),
                        (left, right) -> left
                ));
    }

    private int parseBoundedPositiveInt(String rawValue, int fallback) {
        try {
            int value = Integer.parseInt(rawValue == null ? "" : rawValue.trim());
            return value >= 1 && value <= MAX_LIST_PAGE_SIZE ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public record SwitchSnapshot(String status, String settingValue) {
    }
}
