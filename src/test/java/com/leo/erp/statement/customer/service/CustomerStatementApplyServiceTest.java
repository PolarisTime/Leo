package com.leo.erp.statement.customer.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CustomerStatementApplyService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class CustomerStatementApplyServiceTest {

    @Mock
    private CustomerStatementSourceService sourceService;

    @Mock
    private StatementSettlementSyncService settlementSyncService;

    @InjectMocks
    private CustomerStatementApplyService service;

    private CustomerStatementRequest request(String status) {
        return new CustomerStatementRequest(
                "CS001", null, "客户A", null, "项目A", null, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("5000"), null, null, status, null, List.of(), null);
    }

    private CustomerStatement entity() {
        CustomerStatement entity = new CustomerStatement();
        entity.setId(5L);
        return entity;
    }

    @Test
    void apply_shouldSetFieldsAndBalanceWithDefaultStatus() {
        CustomerStatement entity = entity();
        CustomerStatementRequest request = request(null); // 状态空 → 默认 PENDING_CONFIRM
        when(sourceService.applyItems(any(), any(), any()))
                .thenReturn(new CustomerStatementSourceService.SourceApplyResult(
                        new BigDecimal("5000"), 30L, "结算公司A"));
        when(settlementSyncService.resolveCustomerReceiptAmount(5L)).thenReturn(new BigDecimal("1000"));

        service.apply(entity, request, () -> 100L);

        assertThat(entity.getStatus()).isEqualTo(StatusConstants.PENDING_CONFIRM);
        assertThat(entity.getSalesAmount()).isEqualByComparingTo("5000");
        assertThat(entity.getReceiptAmount()).isEqualByComparingTo("1000");
        assertThat(entity.getClosingAmount()).isEqualByComparingTo("4000");
        assertThat(entity.getSettlementCompanyId()).isEqualTo(30L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("结算公司A");
    }

    @Test
    void apply_shouldRejectNegativeReceiptAmount() {
        CustomerStatement entity = entity();
        CustomerStatementRequest request = request(StatusConstants.PENDING_CONFIRM);
        when(sourceService.applyItems(any(), any(), any()))
                .thenReturn(new CustomerStatementSourceService.SourceApplyResult(
                        new BigDecimal("5000"), 30L, "结算公司A"));
        when(settlementSyncService.resolveCustomerReceiptAmount(5L)).thenReturn(new BigDecimal("-100"));

        assertThatThrownBy(() -> service.apply(entity, request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void apply_shouldRejectReceiptExceedingSalesAmount() {
        CustomerStatement entity = entity();
        CustomerStatementRequest request = request(StatusConstants.PENDING_CONFIRM);
        when(sourceService.applyItems(any(), any(), any()))
                .thenReturn(new CustomerStatementSourceService.SourceApplyResult(
                        new BigDecimal("5000"), 30L, "结算公司A"));
        when(settlementSyncService.resolveCustomerReceiptAmount(5L)).thenReturn(new BigDecimal("6000"));

        assertThatThrownBy(() -> service.apply(entity, request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }
}
