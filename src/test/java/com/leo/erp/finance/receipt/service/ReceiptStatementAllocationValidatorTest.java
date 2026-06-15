package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.service.CustomerStatementQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptStatementAllocationValidatorTest {

    @Test
    void shouldValidateCustomerStatement() {
        CustomerStatementQueryService statementQueryService = mock(CustomerStatementQueryService.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);
        CustomerStatement statement = customerStatement();
        when(statementQueryService.requireActiveById(21L)).thenReturn(statement);
        ReceiptStatementAllocationValidator validator = new ReceiptStatementAllocationValidator(
                mock(ReceiptAllocationRepository.class),
                statementQueryService,
                accessGuard
        );

        CustomerStatement result = validator.validate(
                receiptRequest(),
                StatusConstants.DRAFT,
                1L,
                21L,
                new BigDecimal("100.00"),
                new HashMap<>(),
                1
        );

        assertThat(result).isSameAs(statement);
        verify(accessGuard).assertCurrentUserCanAccess(
                "customer-statement",
                ResourcePermissionCatalog.READ,
                statement
        );
    }

    @Test
    void shouldRejectProjectMismatch() {
        CustomerStatementQueryService statementQueryService = mock(CustomerStatementQueryService.class);
        CustomerStatement statement = customerStatement();
        statement.setProjectName("项目B");
        when(statementQueryService.requireActiveById(21L)).thenReturn(statement);
        ReceiptStatementAllocationValidator validator = new ReceiptStatementAllocationValidator(
                mock(ReceiptAllocationRepository.class),
                statementQueryService,
                mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> validator.validate(
                receiptRequest(),
                StatusConstants.DRAFT,
                1L,
                21L,
                new BigDecimal("100.00"),
                new HashMap<>(),
                1
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账单项目与收款单项目不一致");
    }

    @Test
    void shouldRejectDuplicateCustomerStatement() {
        CustomerStatementQueryService statementQueryService = mock(CustomerStatementQueryService.class);
        when(statementQueryService.requireActiveById(21L)).thenReturn(customerStatement());
        ReceiptStatementAllocationValidator validator = new ReceiptStatementAllocationValidator(
                mock(ReceiptAllocationRepository.class),
                statementQueryService,
                mock(ResourceRecordAccessGuard.class)
        );
        HashMap<Long, BigDecimal> allocatedAmountMap = new HashMap<>();
        allocatedAmountMap.put(21L, new BigDecimal("50.00"));

        assertThatThrownBy(() -> validator.validate(
                receiptRequest(),
                StatusConstants.DRAFT,
                1L,
                21L,
                new BigDecimal("50.00"),
                allocatedAmountMap,
                2
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一收款单不能重复核销同一客户对账单");
    }

    @Test
    void shouldRejectCustomerStatementOverReceipt() {
        ReceiptAllocationRepository allocationRepository = mock(ReceiptAllocationRepository.class);
        CustomerStatementQueryService statementQueryService = mock(CustomerStatementQueryService.class);
        when(statementQueryService.requireActiveById(21L)).thenReturn(customerStatement());
        when(allocationRepository.sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(
                21L,
                ReceiptAllocationService.RECEIPT_STATUS_SETTLED,
                1L
        )).thenReturn(new BigDecimal("950.00"));
        ReceiptStatementAllocationValidator validator = new ReceiptStatementAllocationValidator(
                allocationRepository,
                statementQueryService,
                mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> validator.validate(
                receiptRequest(),
                ReceiptAllocationService.RECEIPT_STATUS_SETTLED,
                1L,
                21L,
                new BigDecimal("100.00"),
                new HashMap<>(),
                1
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("关联客户对账单累计收款金额不能超过销售金额");
    }

    private ReceiptRequest receiptRequest() {
        return new ReceiptRequest(
                "SK-001",
                "CUST-001",
                "客户A",
                1001L,
                "项目A",
                null,
                LocalDate.of(2026, 4, 26),
                "银行转账",
                new BigDecimal("100.00"),
                StatusConstants.DRAFT,
                "财务A",
                null,
                List.of()
        );
    }

    private CustomerStatement customerStatement() {
        CustomerStatement statement = new CustomerStatement();
        statement.setId(21L);
        statement.setCustomerName("客户A");
        statement.setCustomerCode("CUST-001");
        statement.setProjectName("项目A");
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setSalesAmount(new BigDecimal("1000.00"));
        return statement;
    }
}
