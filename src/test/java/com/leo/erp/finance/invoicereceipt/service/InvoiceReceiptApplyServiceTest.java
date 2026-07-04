package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.finance.common.service.InvoiceAmountCalculator;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoiceReceiptApplyServiceTest {

    @Test
    void shouldTrimExplicitInvoiceTitle() {
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        InvoiceReceiptSourceService sourceService = mock(InvoiceReceiptSourceService.class);
        InvoiceAmountCalculator amountCalculator = mock(InvoiceAmountCalculator.class);
        InvoiceReceiptApplyService service = new InvoiceReceiptApplyService(
                workflowTransitionGuard,
                sourceService,
                amountCalculator
        );
        InvoiceReceipt entity = new InvoiceReceipt();
        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "SP-001",
                "INV-001",
                "供应商A",
                " 发票抬头 ",
                LocalDate.of(2026, 4, 26),
                "增值税专票",
                new BigDecimal("100.00"),
                new BigDecimal("13.00"),
                "草稿",
                "财务A",
                "备注",
                List.of()
        );
        when(sourceService.applyItems(eq(entity), eq(request.items()), eq("供应商A"), any()))
                .thenReturn(new BigDecimal("100.00"));
        when(amountCalculator.resolve("收票", new BigDecimal("100.00"), request.amount(), request.taxAmount()))
                .thenReturn(new InvoiceAmountCalculator.InvoiceAmounts(new BigDecimal("100.00"), new BigDecimal("13.00")));

        service.apply(entity, request, () -> 1L);

        assertThat(entity.getInvoiceTitle()).isEqualTo("发票抬头");
        assertThat(entity.getAmount()).isEqualByComparingTo("100.00");
        assertThat(entity.getTaxAmount()).isEqualByComparingTo("13.00");
    }
}
