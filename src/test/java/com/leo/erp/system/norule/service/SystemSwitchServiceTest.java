package com.leo.erp.system.norule.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemSwitchServiceTest {

    @Test
    void shouldLoadKnownSwitchesThroughRedisSnapshot() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        when(repository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.SHOW_SNOWFLAKE_ID_SWITCH, "正常", ""),
                rule(SystemSwitchService.MAX_CONCURRENT_SESSIONS_SWITCH, "正常", "8"),
                rule(SystemSwitchService.OPERATION_LOG_DETAILED_PAGE_ACTIONS_SWITCH, "正常", "QUERY,EDIT"),
                rule(SystemSwitchService.USE_SNOWFLAKE_ID_AS_BUSINESS_NO_SWITCH, "禁用", "")
        ));
        when(redisJsonCacheSupport.getOrLoad(
                eq(SystemSwitchService.SWITCH_CACHE_KEY),
                any(Duration.class),
                any(TypeReference.class),
                any(Supplier.class)
        )).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get());

        SystemSwitchService service = new SystemSwitchService(repository, redisJsonCacheSupport);

        assertThat(service.shouldShowSnowflakeId()).isTrue();
        assertThat(service.shouldUseSnowflakeIdAsBusinessNo()).isFalse();
        assertThat(service.getMaxConcurrentSessions()).isEqualTo(8);
        assertThat(service.getDefaultListPageSize()).isEqualTo(20);
        assertThat(service.shouldRecordDetailedPageAction("edit")).isTrue();
        assertThat(service.shouldRecordDetailedPageAction("delete")).isFalse();
        verify(redisJsonCacheSupport, atLeastOnce()).getOrLoad(
                eq(SystemSwitchService.SWITCH_CACHE_KEY),
                any(Duration.class),
                any(TypeReference.class),
                any(Supplier.class)
        );
    }

    @Test
    void shouldReadConfiguredDefaultListPageSize() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING, "正常", "50")
        ));

        SystemSwitchService service = new SystemSwitchService(repository);

        assertThat(service.getDefaultListPageSize()).isEqualTo(50);
    }

    @Test
    void shouldReadStatementGeneratorDefaultRules() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH, "正常", ""),
                rule(SystemSwitchService.SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE_SWITCH, "禁用", "")
        ));

        SystemSwitchService service = new SystemSwitchService(repository);

        assertThat(service.shouldDefaultCustomerStatementReceiptAmountToZero()).isTrue();
        assertThat(service.shouldDefaultSupplierStatementToFullPayment()).isFalse();
    }

    @Test
    void shouldReadSnowflakeBusinessNoSwitch() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.USE_SNOWFLAKE_ID_AS_BUSINESS_NO_SWITCH, "正常", "ON")
        ));

        SystemSwitchService service = new SystemSwitchService(repository);

        assertThat(service.shouldUseSnowflakeIdAsBusinessNo()).isTrue();
    }

    @Test
    void shouldFallbackForBlankUnknownAndMissingSwitches() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of());
        when(repository.findBySettingCodeAndDeletedFlagFalse("CUSTOM_SWITCH")).thenReturn(Optional.empty());

        SystemSwitchService service = new SystemSwitchService(repository);

        assertThat(service.isEnabled(null)).isFalse();
        assertThat(service.isEnabled("  ")).isFalse();
        assertThat(service.isEnabled("CUSTOM_SWITCH")).isFalse();
        assertThat(service.getMaxConcurrentSessions()).isEqualTo(3);
        assertThat(service.getDefaultListPageSize()).isEqualTo(20);
        assertThat(service.shouldRecordDetailedPageAction(null)).isFalse();
        assertThat(service.shouldRecordDetailedPageAction(" ")).isFalse();
        assertThat(service.shouldRecordDetailedPageAction("query")).isTrue();
    }

    @Test
    void shouldReadUnknownSwitchDirectlyFromRepository() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.findBySettingCodeAndDeletedFlagFalse("CUSTOM_SWITCH")).thenReturn(
                Optional.of(rule("CUSTOM_SWITCH", "正常", ""))
        );

        SystemSwitchService service = new SystemSwitchService(repository);

        assertThat(service.isEnabled(" CUSTOM_SWITCH ")).isTrue();
    }

    @Test
    void shouldFallbackInvalidSessionAndPageSizeValues() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.MAX_CONCURRENT_SESSIONS_SWITCH, "正常", "-1"),
                rule(SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING, "正常", "201")
        ));

        SystemSwitchService service = new SystemSwitchService(repository);

        assertThat(service.getMaxConcurrentSessions()).isEqualTo(3);
        assertThat(service.getDefaultListPageSize()).isEqualTo(20);
    }

    @Test
    void shouldFallbackNonNumericSessionAndPageSizeValues() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.MAX_CONCURRENT_SESSIONS_SWITCH, "正常", "abc"),
                rule(SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING, "正常", "abc")
        ));

        SystemSwitchService service = new SystemSwitchService(repository);

        assertThat(service.getMaxConcurrentSessions()).isEqualTo(3);
        assertThat(service.getDefaultListPageSize()).isEqualTo(20);
    }

    @Test
    void shouldResolveHiddenAuditedStatusesFromSwitchSample() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH, "正常", " AUDITED, DONE ,, ")
        ));
        when(repository.findBySettingCodeAndDeletedFlagFalse(
                SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH
        )).thenReturn(Optional.of(
                rule(SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH, "正常", " AUDITED, DONE ,, ")
        ));

        SystemSwitchService service = new SystemSwitchService(repository);

        assertThat(service.shouldHideAuditedListRecords()).isTrue();
        assertThat(service.getHiddenAuditedStatuses()).containsExactlyInAnyOrder("AUDITED", "DONE");
    }

    @Test
    void shouldUseDefaultHiddenAuditedStatusForBlankOrOnSample() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH, "正常", "ON")
        ));
        when(repository.findBySettingCodeAndDeletedFlagFalse(
                SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH
        )).thenReturn(Optional.of(
                rule(SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH, "正常", "ON")
        ));

        SystemSwitchService service = new SystemSwitchService(repository);

        assertThat(service.getHiddenAuditedStatuses()).containsExactly("已审核");
    }

    @Test
    void shouldReturnEmptyHiddenAuditedStatusesWhenSwitchDisabledOrOff() {
        NoRuleRepository disabledRepository = mock(NoRuleRepository.class);
        when(disabledRepository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH, "禁用", "AUDITED")
        ));
        SystemSwitchService disabledService = new SystemSwitchService(disabledRepository);
        assertThat(disabledService.getHiddenAuditedStatuses()).isEmpty();

        NoRuleRepository offRepository = mock(NoRuleRepository.class);
        when(offRepository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH, "正常", "OFF")
        ));
        when(offRepository.findBySettingCodeAndDeletedFlagFalse(
                SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH
        )).thenReturn(Optional.of(
                rule(SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH, "正常", "OFF")
        ));
        SystemSwitchService offService = new SystemSwitchService(offRepository);
        assertThat(offService.getHiddenAuditedStatuses()).isEmpty();
    }

    @Test
    void shouldFallbackDetailedPageActionsWhenConfiguredValueIsBlankOrUnknown() {
        NoRuleRepository blankRepository = mock(NoRuleRepository.class);
        when(blankRepository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.OPERATION_LOG_DETAILED_PAGE_ACTIONS_SWITCH, "正常", " ")
        ));
        SystemSwitchService blankService = new SystemSwitchService(blankRepository);
        assertThat(blankService.shouldRecordDetailedPageAction("delete")).isTrue();

        NoRuleRepository unknownRepository = mock(NoRuleRepository.class);
        when(unknownRepository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.OPERATION_LOG_DETAILED_PAGE_ACTIONS_SWITCH, "正常", "UNKNOWN")
        ));
        SystemSwitchService unknownService = new SystemSwitchService(unknownRepository);
        assertThat(unknownService.shouldRecordDetailedPageAction("print")).isTrue();
    }

    @Test
    void shouldExposeNamedSwitchHelpers() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.findBySettingCodeInAndDeletedFlagFalse(any())).thenReturn(List.of(
                rule(SystemSwitchService.BATCH_NO_AUTO_GENERATE_SWITCH, "正常", ""),
                rule(SystemSwitchService.OPERATION_LOG_RECORD_ALL_WRITE_SWITCH, "正常", ""),
                rule(SystemSwitchService.OPERATION_LOG_DETAILED_PAGE_ACTIONS_SWITCH, "正常", ""),
                rule(SystemSwitchService.AUTH_OPERATION_LOG_SWITCH, "正常", ""),
                rule(SystemSwitchService.FORCE_USER_TOTP_ON_FIRST_LOGIN_SWITCH, "正常", ""),
                rule(SystemSwitchService.FORCE_BATCH_MANAGEMENT_SWITCH, "正常", ""),
                rule(SystemSwitchService.FORBID_DISABLE_2FA_SWITCH, "正常", ""),
                rule(SystemSwitchService.ADMIN_VIEW_DELETED_RECORDS_SWITCH, "正常", ""),
                rule(SystemSwitchService.LOGIN_CAPTCHA_SWITCH, "正常", ""),
                rule(SystemSwitchService.UI_WATERMARK_ENABLED_SWITCH, "正常", ""),
                rule(SystemSwitchService.ATTACHMENT_WATERMARK_SWITCH, "正常", ""),
                rule(SystemSwitchService.OOBE_COMPLETED_SWITCH, "正常", "")
        ));
        when(repository.findBySettingCodeAndDeletedFlagFalse(
                SystemSwitchService.ATTACHMENT_WATERMARK_SWITCH
        )).thenReturn(Optional.of(rule(SystemSwitchService.ATTACHMENT_WATERMARK_SWITCH, "正常", "")));

        SystemSwitchService service = new SystemSwitchService(repository);

        assertThat(service.shouldAutoGenerateBatchNo()).isTrue();
        assertThat(service.shouldAutoRecordAllWriteOperations()).isTrue();
        assertThat(service.shouldRecordDetailedPageActions()).isTrue();
        assertThat(service.shouldRecordAuthenticationOperationLogs()).isTrue();
        assertThat(service.shouldForceUserTotpOnFirstLogin()).isTrue();
        assertThat(service.shouldForceBatchManagement()).isTrue();
        assertThat(service.shouldForbidDisable2fa()).isTrue();
        assertThat(service.shouldAdminSeeDeletedRecords()).isTrue();
        assertThat(service.shouldRequireLoginCaptcha()).isTrue();
        assertThat(service.shouldWatermarkAttachments()).isTrue();
        assertThat(service.isOobeCompleted()).isTrue();
    }

    @Test
    void shouldEvictRedisCacheWhenAvailable() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        SystemSwitchService service = new SystemSwitchService(repository, redisJsonCacheSupport);

        service.evictCache();

        verify(redisJsonCacheSupport).delete(SystemSwitchService.SWITCH_CACHE_KEY);
    }

    @Test
    void shouldIgnoreCacheEvictionWhenRedisUnavailable() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        SystemSwitchService service = new SystemSwitchService(repository);

        service.evictCache();

        assertThat(service.shouldShowSnowflakeId()).isFalse();
    }

    private NoRule rule(String code, String status, String sampleNo) {
        NoRule rule = new NoRule();
        rule.setSettingCode(code);
        rule.setStatus(status);
        rule.setSampleNo(sampleNo);
        return rule;
    }
}
