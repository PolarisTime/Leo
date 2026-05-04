package com.leo.erp.system.norule.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SystemSwitchService {

    public static final String BATCH_NO_AUTO_GENERATE_SWITCH = "SYS_BATCH_NO_AUTO_GENERATE";
    public static final String OPERATION_LOG_RECORD_ALL_WRITE_SWITCH = "SYS_OPERATION_LOG_RECORD_ALL_WRITE";
    public static final String OPERATION_LOG_DETAILED_PAGE_ACTIONS_SWITCH = "SYS_OPERATION_LOG_DETAILED_PAGE_ACTIONS";
    public static final String AUTH_OPERATION_LOG_SWITCH = "SYS_OPERATION_LOG_RECORD_AUTH_EVENTS";
    public static final String FORCE_USER_TOTP_ON_FIRST_LOGIN_SWITCH = "SYS_FORCE_USER_TOTP_ON_FIRST_LOGIN";
    public static final String FORCE_BATCH_MANAGEMENT_SWITCH = "SYS_FORCE_BATCH_MANAGEMENT";
    public static final String FORBID_DISABLE_2FA_SWITCH = "SYS_FORBID_DISABLE_2FA";
    public static final String MAX_CONCURRENT_SESSIONS_SWITCH = "SYS_MAX_CONCURRENT_SESSIONS";
    public static final String HIDE_AUDITED_LIST_RECORDS_SWITCH = "UI_HIDE_AUDITED_LIST_RECORDS";
    public static final String SHOW_SNOWFLAKE_ID_SWITCH = "UI_SHOW_SNOWFLAKE_ID";
    public static final String LOGIN_CAPTCHA_SWITCH = "SYS_LOGIN_CAPTCHA";
    public static final String SWITCH_CACHE_KEY = "leo:system:switches";
    private static final Duration SWITCH_CACHE_TTL = Duration.ofMinutes(5);
    private static final TypeReference<Map<String, SwitchSnapshot>> SWITCH_MAP_TYPE = new TypeReference<>() { };
    private static final int DEFAULT_MAX_SESSIONS = 3;
    private static final Set<String> DEFAULT_DETAILED_PAGE_ACTIONS = Set.of(
            "QUERY", "DETAIL", "CREATE", "EDIT", "DELETE", "AUDIT", "EXPORT", "PRINT"
    );
    private static final Set<String> KNOWN_SWITCH_CODES = Set.of(
            BATCH_NO_AUTO_GENERATE_SWITCH,
            OPERATION_LOG_RECORD_ALL_WRITE_SWITCH,
            OPERATION_LOG_DETAILED_PAGE_ACTIONS_SWITCH,
            AUTH_OPERATION_LOG_SWITCH,
            FORCE_USER_TOTP_ON_FIRST_LOGIN_SWITCH,
            FORCE_BATCH_MANAGEMENT_SWITCH,
            FORBID_DISABLE_2FA_SWITCH,
            MAX_CONCURRENT_SESSIONS_SWITCH,
            HIDE_AUDITED_LIST_RECORDS_SWITCH,
            SHOW_SNOWFLAKE_ID_SWITCH,
            LOGIN_CAPTCHA_SWITCH
    );

    private final NoRuleRepository noRuleRepository;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    @Autowired
    public SystemSwitchService(NoRuleRepository noRuleRepository, RedisJsonCacheSupport redisJsonCacheSupport) {
        this.noRuleRepository = noRuleRepository;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
    }

    public SystemSwitchService(NoRuleRepository noRuleRepository) {
        this.noRuleRepository = noRuleRepository;
        this.redisJsonCacheSupport = null;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String settingCode) {
        return findSwitch(settingCode)
                .map(rule -> "正常".equals(rule.status()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean shouldAutoRecordAllWriteOperations() {
        return isEnabled(OPERATION_LOG_RECORD_ALL_WRITE_SWITCH);
    }

    @Transactional(readOnly = true)
    public boolean shouldRecordDetailedPageActions() {
        return isEnabled(OPERATION_LOG_DETAILED_PAGE_ACTIONS_SWITCH);
    }

    @Transactional(readOnly = true)
    public boolean shouldRecordDetailedPageAction(String actionKey) {
        if (actionKey == null || actionKey.isBlank()) {
            return false;
        }
        return resolveDetailedPageActionKeys().contains(actionKey.trim().toUpperCase());
    }

    @Transactional(readOnly = true)
    public boolean shouldAutoGenerateBatchNo() {
        return isEnabled(BATCH_NO_AUTO_GENERATE_SWITCH);
    }

    @Transactional(readOnly = true)
    public boolean shouldRecordAuthenticationOperationLogs() {
        return isEnabled(AUTH_OPERATION_LOG_SWITCH);
    }

    @Transactional(readOnly = true)
    public boolean shouldForceUserTotpOnFirstLogin() {
        return isEnabled(FORCE_USER_TOTP_ON_FIRST_LOGIN_SWITCH);
    }

    @Transactional(readOnly = true)
    public boolean shouldForceBatchManagement() {
        return isEnabled(FORCE_BATCH_MANAGEMENT_SWITCH);
    }

    @Transactional(readOnly = true)
    public boolean shouldForbidDisable2fa() {
        return isEnabled(FORBID_DISABLE_2FA_SWITCH);
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
        Set<String> statuses = noRuleRepository.findBySettingCodeAndDeletedFlagFalse(HIDE_AUDITED_LIST_RECORDS_SWITCH)
                .map(rule -> {
                    String sampleNo = rule.getSampleNo();
                    if (sampleNo == null || sampleNo.isBlank() || "ON".equals(sampleNo.trim())) {
                        return Set.of(com.leo.erp.common.support.StatusConstants.AUDITED);
                    }
                    if ("OFF".equals(sampleNo.trim())) {
                        return Set.<String>of();
                    }
                    return Arrays.stream(sampleNo.split(","))
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
    public boolean shouldRequireLoginCaptcha() {
        return isEnabled(LOGIN_CAPTCHA_SWITCH);
    }

    @Transactional(readOnly = true)
    public int getMaxConcurrentSessions() {
        return findSwitch(MAX_CONCURRENT_SESSIONS_SWITCH)
                .filter(rule -> "正常".equals(rule.status()))
                .map(rule -> {
                    try {
                        int val = Integer.parseInt(rule.sampleNo());
                        return val > 0 ? val : DEFAULT_MAX_SESSIONS;
                    } catch (NumberFormatException e) {
                        return DEFAULT_MAX_SESSIONS;
                    }
                })
                .orElse(DEFAULT_MAX_SESSIONS);
    }

    public void evictCache() {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.delete(SWITCH_CACHE_KEY);
        }
    }

    private Set<String> resolveDetailedPageActionKeys() {
        return findSwitch(OPERATION_LOG_DETAILED_PAGE_ACTIONS_SWITCH)
                .map(rule -> parseDetailedPageActionKeys(rule.sampleNo()))
                .orElse(DEFAULT_DETAILED_PAGE_ACTIONS);
    }

    private Optional<SwitchSnapshot> findSwitch(String settingCode) {
        if (settingCode == null || settingCode.isBlank() || noRuleRepository == null) {
            return Optional.empty();
        }
        String normalizedCode = settingCode.trim();
        if (KNOWN_SWITCH_CODES.contains(normalizedCode)) {
            return Optional.ofNullable(loadKnownSwitches().get(normalizedCode));
        }
        return noRuleRepository.findBySettingCodeAndDeletedFlagFalse(normalizedCode)
                .map(rule -> new SwitchSnapshot(rule.getStatus(), rule.getSampleNo()));
    }

    private Map<String, SwitchSnapshot> loadKnownSwitches() {
        if (noRuleRepository == null) {
            return Map.of();
        }
        if (redisJsonCacheSupport == null) {
            return loadKnownSwitchesFromDatabase();
        }
        return redisJsonCacheSupport.getOrLoad(
                SWITCH_CACHE_KEY,
                SWITCH_CACHE_TTL,
                SWITCH_MAP_TYPE,
                this::loadKnownSwitchesFromDatabase
        );
    }

    private Map<String, SwitchSnapshot> loadKnownSwitchesFromDatabase() {
        return noRuleRepository.findBySettingCodeInAndDeletedFlagFalse(KNOWN_SWITCH_CODES).stream()
                .collect(Collectors.toMap(
                        rule -> rule.getSettingCode().trim(),
                        rule -> new SwitchSnapshot(rule.getStatus(), rule.getSampleNo()),
                        (left, right) -> left
                ));
    }

    private Set<String> parseDetailedPageActionKeys(String sampleNo) {
        if (sampleNo == null || sampleNo.isBlank()) {
            return DEFAULT_DETAILED_PAGE_ACTIONS;
        }
        Set<String> selected = Arrays.stream(sampleNo.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(String::toUpperCase)
                .filter(DEFAULT_DETAILED_PAGE_ACTIONS::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return selected.isEmpty() ? DEFAULT_DETAILED_PAGE_ACTIONS : selected;
    }

    public record SwitchSnapshot(String status, String sampleNo) {
    }
}
