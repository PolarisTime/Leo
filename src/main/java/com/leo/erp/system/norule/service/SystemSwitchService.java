package com.leo.erp.system.norule.service;

import com.leo.erp.system.norule.repository.NoRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private static final int DEFAULT_MAX_SESSIONS = 3;
    private static final Set<String> DEFAULT_DETAILED_PAGE_ACTIONS = Set.of(
            "QUERY", "DETAIL", "CREATE", "EDIT", "DELETE", "AUDIT", "EXPORT", "PRINT"
    );

    private final NoRuleRepository noRuleRepository;

    public SystemSwitchService(NoRuleRepository noRuleRepository) {
        this.noRuleRepository = noRuleRepository;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String settingCode) {
        return noRuleRepository.findBySettingCodeAndDeletedFlagFalse(settingCode)
                .map(rule -> "正常".equals(rule.getStatus()))
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
    public boolean shouldShowSnowflakeId() {
        return isEnabled(SHOW_SNOWFLAKE_ID_SWITCH);
    }

    @Transactional(readOnly = true)
    public int getMaxConcurrentSessions() {
        return noRuleRepository.findBySettingCodeAndDeletedFlagFalse(MAX_CONCURRENT_SESSIONS_SWITCH)
                .filter(rule -> "正常".equals(rule.getStatus()))
                .map(rule -> {
                    try {
                        int val = Integer.parseInt(rule.getSampleNo());
                        return val > 0 ? val : DEFAULT_MAX_SESSIONS;
                    } catch (NumberFormatException e) {
                        return DEFAULT_MAX_SESSIONS;
                    }
                })
                .orElse(DEFAULT_MAX_SESSIONS);
    }

    private Set<String> resolveDetailedPageActionKeys() {
        return noRuleRepository.findBySettingCodeAndDeletedFlagFalse(OPERATION_LOG_DETAILED_PAGE_ACTIONS_SWITCH)
                .map(rule -> parseDetailedPageActionKeys(rule.getSampleNo()))
                .orElse(DEFAULT_DETAILED_PAGE_ACTIONS);
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
}
