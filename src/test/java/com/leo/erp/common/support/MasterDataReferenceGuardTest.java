package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterDataReferenceGuardTest {

    @Test
    void shouldSkipBlankValuesAndCheckTrustedReferences() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(0L);
        MasterDataReferenceGuard guard = new MasterDataReferenceGuard(jdbc);

        guard.assertNoReferences("该客户", List.of(
                ReferenceCheck.active("so_sales_order", "customer_code", "C001"),
                ReferenceCheck.active("fm_receipt", "customer_code", " ")
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sqlCaptor.capture(), eq(Long.class), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("FROM so_sales_order")
                .contains("deleted_flag = false")
                .contains("customer_code = ?");
    }

    @Test
    void shouldThrowExceptionWhenReferenceExists() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(3L);
        MasterDataReferenceGuard guard = new MasterDataReferenceGuard(jdbc);

        assertThatThrownBy(() -> guard.assertNoReferences("该供应商", List.of(
                ReferenceCheck.activeWhen("fm_payment", "counterparty_code", "S001", "business_type = ?", "供应商")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该供应商已被业务或主数据引用")
                .hasMessageContaining("fm_payment.counterparty_code")
                .hasMessageContaining("3 条记录");
    }
}
