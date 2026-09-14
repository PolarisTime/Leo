package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.statement.api.CustomerStatementApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReceiptStatementAllocationValidator：收款核销候选显式排除红字对账单。
 */
@ExtendWith(MockitoExtension.class)
class ReceiptStatementAllocationValidatorTest {

    @Mock
    private ReceiptAllocationRepository receiptAllocationRepository;

    @Mock
    private CustomerStatementApi customerStatementApi;

    @InjectMocks
    private ReceiptStatementAllocationValidator validator;

    private ReceiptRequest request(BigDecimal amount) {
        return new ReceiptRequest("RC001", 10L, "C001", "客户A", 20L, "项目A", 30L, "结算主体A", null,
                LocalDate.of(2026, 9, 1), "电汇", amount, StatusConstants.AUDITED, "张三", null, List.of());
    }

    private CustomerStatementApi.Snapshot snapshot(String direction) {
        return new CustomerStatementApi.Snapshot(21L, "ST001", 10L, "C001", "客户A", 20L, "项目A",
                30L, "结算主体A", new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                StatusConstants.CONFIRMED, direction);
    }

    @Test
    void validate_shouldRejectRedStatement() {
        when(customerStatementApi.requireActiveAllocatableById(21L))
                .thenThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "红字对账单不参与收款核销"));

        assertThatThrownBy(() -> validator.validate(request(new BigDecimal("100.00")),
                StatusConstants.AUDITED, 1L, 21L, new BigDecimal("100.00"), new HashMap<>(), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("红字对账单不参与收款核销");
    }

    @Test
    void validate_shouldAcceptBlueStatement() {
        CustomerStatementApi.Snapshot snapshot = snapshot(StatusConstants.STATEMENT_DIRECTION_BLUE);
        when(customerStatementApi.requireActiveAllocatableById(21L)).thenReturn(snapshot);
        when(receiptAllocationRepository
                .sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(
                        21L, ReceiptAllocationService.RECEIPT_STATUS_SETTLED, 1L))
                .thenReturn(BigDecimal.ZERO);

        CustomerStatementApi.Snapshot result = validator.validate(request(new BigDecimal("100.00")),
                StatusConstants.AUDITED, 1L, 21L, new BigDecimal("100.00"), new HashMap<>(), 1);

        assertThat(result).isSameAs(snapshot);
        verify(customerStatementApi).requireActiveAllocatableById(21L);
    }
}
