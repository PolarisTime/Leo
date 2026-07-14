package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceiptAllocationServiceTest {

    @Test
    void shouldMergeCustomerCodeWhenCurrentCodeExists() {
        ReceiptAllocationService service = new ReceiptAllocationService(mock(ReceiptStatementAllocationValidator.class));

        assertThat(service.mergeCustomerCode(" CUST-001 ", null)).isEqualTo("CUST-001");
        assertThat(service.mergeCustomerCode(" CUST-001 ", "CUST-001")).isEqualTo("CUST-001");
        assertThatThrownBy(() -> service.mergeCustomerCode("CUST-001", "CUST-002"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一收款单不能核销不同客户编码的对账单");
    }

    @Test
    void shouldNormalizeNullItemsFromSourceStatementId() {
        ReceiptAllocationService service = new ReceiptAllocationService(mock(ReceiptStatementAllocationValidator.class));

        List<ReceiptAllocationRequest> requests = service.normalizeAllocationRequests(receiptRequest(
                21L,
                new BigDecimal("100.00"),
                null
        ));

        assertThat(requests).singleElement()
                .satisfies(request -> {
                    assertThat(request.id()).isNull();
                    assertThat(request.sourceStatementId()).isEqualTo(21L);
                    assertThat(request.allocatedAmount()).isEqualByComparingTo("100.00");
                });
    }

    @Test
    void shouldApplySettlementCompanyFromAllocations() {
        ReceiptStatementAllocationValidator validator = mock(ReceiptStatementAllocationValidator.class);
        when(validator.validate(
                any(ReceiptRequest.class),
                eq(StatusConstants.DRAFT),
                eq(1L),
                eq(21L),
                any(BigDecimal.class),
                any(),
                eq(1)
        )).thenReturn(customerStatement(21L, "CUST-001", 1001L, "结算主体A"));
        ReceiptAllocationService service = new ReceiptAllocationService(validator);
        Receipt receipt = receipt();

        ReceiptAllocationService.AllocationApplyResult result = service.applyAllocations(
                receipt,
                receiptRequest(null, new BigDecimal("100.00"), List.of(
                        new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00"))
                )),
                StatusConstants.DRAFT,
                new AtomicLong(100)::incrementAndGet
        );

        assertThat(result.customerCode()).isEqualTo("CUST-001");
        assertThat(result.settlementCompanyId()).isEqualTo(1001L);
        assertThat(result.settlementCompanyName()).isEqualTo("结算主体A");
        assertThat(result.totalAllocatedAmount()).isEqualByComparingTo("100.00");
        assertThat(result.allocationEmpty()).isFalse();
        assertThat(receipt.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getId()).isEqualTo(101L);
                    assertThat(item.getReceipt()).isSameAs(receipt);
                    assertThat(item.getLineNo()).isEqualTo(1);
                    assertThat(item.getSourceStatementId()).isEqualTo(21L);
                    assertThat(item.getSourceCustomerStatementId()).isEqualTo(21L);
                    assertThat(item.getAllocatedAmount()).isEqualByComparingTo("100.00");
                });
    }

    @Test
    void shouldApplySettlementCompanyNameWhenCompanyIdMissing() {
        ReceiptStatementAllocationValidator validator = mock(ReceiptStatementAllocationValidator.class);
        when(validator.validate(
                any(ReceiptRequest.class),
                eq(StatusConstants.DRAFT),
                eq(1L),
                eq(21L),
                any(BigDecimal.class),
                any(),
                eq(1)
        )).thenReturn(customerStatement(21L, "CUST-001", null, "结算主体A"));
        ReceiptAllocationService service = new ReceiptAllocationService(validator);

        ReceiptAllocationService.AllocationApplyResult result = service.applyAllocations(
                receipt(),
                receiptRequest(null, new BigDecimal("100.00"), List.of(
                        new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00"))
                )),
                StatusConstants.DRAFT,
                new AtomicLong(100)::incrementAndGet
        );

        assertThat(result.settlementCompanyId()).isNull();
        assertThat(result.settlementCompanyName()).isEqualTo("结算主体A");
    }

    @Test
    void shouldKeepSettlementCompanyWhenAllocationsUseSameCompany() {
        ReceiptStatementAllocationValidator validator = mock(ReceiptStatementAllocationValidator.class);
        when(validator.validate(
                any(ReceiptRequest.class),
                eq(StatusConstants.DRAFT),
                eq(1L),
                anyLong(),
                any(BigDecimal.class),
                any(),
                anyInt()
        )).thenAnswer(invocation -> customerStatement(
                invocation.getArgument(3),
                "CUST-001",
                1001L,
                "结算主体A"
        ));
        ReceiptAllocationService service = new ReceiptAllocationService(validator);

        ReceiptAllocationService.AllocationApplyResult result = service.applyAllocations(
                receipt(),
                receiptRequest(null, new BigDecimal("100.00"), List.of(
                        new ReceiptAllocationRequest(null, 21L, new BigDecimal("40.00")),
                        new ReceiptAllocationRequest(null, 22L, new BigDecimal("60.00"))
                )),
                StatusConstants.DRAFT,
                new AtomicLong(100)::incrementAndGet
        );

        assertThat(result.settlementCompanyId()).isEqualTo(1001L);
        assertThat(result.settlementCompanyName()).isEqualTo("结算主体A");
        assertThat(result.totalAllocatedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldNotExposeIncompleteNewAllocationsToValidatorQueries() {
        Receipt receipt = receipt();
        ReceiptStatementAllocationValidator validator = mock(ReceiptStatementAllocationValidator.class);
        when(validator.validate(
                any(ReceiptRequest.class),
                eq(StatusConstants.AUDITED),
                eq(1L),
                anyLong(),
                any(BigDecimal.class),
                any(),
                anyInt()
        )).thenAnswer(invocation -> {
            assertThat(receipt.getItems())
                    .as("validator 查询触发 flush 时不应暴露未完整赋值的新核销行")
                    .noneMatch(item -> item.getReceipt() == null
                            || item.getLineNo() == null
                            || item.getSourceStatementId() == null
                            || item.getAllocatedAmount() == null);
            Long statementId = invocation.getArgument(3);
            return customerStatement(statementId, "CUST-001", 1001L, "结算主体A");
        });
        ReceiptAllocationService service = new ReceiptAllocationService(validator);

        ReceiptAllocationService.AllocationApplyResult result = service.applyAllocations(
                receipt,
                receiptRequest(null, new BigDecimal("100.00"), List.of(
                        new ReceiptAllocationRequest(null, 21L, new BigDecimal("40.00")),
                        new ReceiptAllocationRequest(null, 22L, new BigDecimal("60.00"))
                )),
                StatusConstants.AUDITED,
                new AtomicLong(100)::incrementAndGet
        );

        assertThat(result.totalAllocatedAmount()).isEqualByComparingTo("100.00");
        assertThat(receipt.getItems()).hasSize(2).allSatisfy(item -> {
            assertThat(item.getReceipt()).isSameAs(receipt);
            assertThat(item.getLineNo()).isNotNull();
            assertThat(item.getSourceStatementId()).isNotNull();
            assertThat(item.getAllocatedAmount()).isNotNull();
        });
    }

    @Test
    void shouldRejectDifferentSettlementCompanies() {
        ReceiptStatementAllocationValidator validator = mock(ReceiptStatementAllocationValidator.class);
        when(validator.validate(
                any(ReceiptRequest.class),
                eq(StatusConstants.DRAFT),
                eq(1L),
                anyLong(),
                any(BigDecimal.class),
                any(),
                anyInt()
        )).thenAnswer(invocation -> {
            Long statementId = invocation.getArgument(3);
            if (statementId.equals(21L)) {
                return customerStatement(statementId, "CUST-001", 1001L, "结算主体A");
            }
            return customerStatement(statementId, "CUST-001", 1002L, "结算主体B");
        });
        ReceiptAllocationService service = new ReceiptAllocationService(validator);

        assertThatThrownBy(() -> service.applyAllocations(
                receipt(),
                receiptRequest(null, new BigDecimal("100.00"), List.of(
                        new ReceiptAllocationRequest(null, 21L, new BigDecimal("40.00")),
                        new ReceiptAllocationRequest(null, 22L, new BigDecimal("60.00"))
                )),
                StatusConstants.DRAFT,
                new AtomicLong(100)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行客户对账单结算主体与收款单不一致");
    }

    private Receipt receipt() {
        Receipt receipt = new Receipt();
        receipt.setId(1L);
        receipt.setReceiptNo("SK-001");
        receipt.setCustomerName("客户A");
        receipt.setProjectName("项目A");
        receipt.setReceiptDate(LocalDate.of(2026, 4, 26));
        receipt.setPayType("银行转账");
        receipt.setAmount(new BigDecimal("100.00"));
        receipt.setStatus(StatusConstants.DRAFT);
        receipt.setOperatorName("财务A");
        receipt.setItems(new ArrayList<>());
        return receipt;
    }

    private ReceiptRequest receiptRequest(Long sourceStatementId,
                                          BigDecimal amount,
                                          List<ReceiptAllocationRequest> items) {
        return new ReceiptRequest(
                "SK-001",
                "客户A",
                "项目A",
                sourceStatementId,
                LocalDate.of(2026, 4, 26),
                "银行转账",
                amount,
                StatusConstants.DRAFT,
                "财务A",
                null,
                items
        );
    }

    private CustomerStatement customerStatement(Long id,
                                                String customerCode,
                                                Long settlementCompanyId,
                                                String settlementCompanyName) {
        CustomerStatement statement = new CustomerStatement();
        statement.setId(id);
        statement.setCustomerId(101L);
        statement.setCustomerCode(customerCode);
        statement.setProjectId(1001L);
        statement.setSettlementCompanyId(settlementCompanyId);
        statement.setSettlementCompanyName(settlementCompanyName);
        return statement;
    }
}
