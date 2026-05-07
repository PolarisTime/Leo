package com.leo.erp.system.norule.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
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
                rule(SystemSwitchService.OPERATION_LOG_DETAILED_PAGE_ACTIONS_SWITCH, "正常", "QUERY,EDIT")
        ));
        when(redisJsonCacheSupport.getOrLoad(
                eq(SystemSwitchService.SWITCH_CACHE_KEY),
                any(Duration.class),
                any(TypeReference.class),
                any(Supplier.class)
        )).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get());

        SystemSwitchService service = new SystemSwitchService(repository, redisJsonCacheSupport);

        assertThat(service.shouldShowSnowflakeId()).isTrue();
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

    private NoRule rule(String code, String status, String sampleNo) {
        NoRule rule = new NoRule();
        rule.setSettingCode(code);
        rule.setStatus(status);
        rule.setSampleNo(sampleNo);
        return rule;
    }
}
