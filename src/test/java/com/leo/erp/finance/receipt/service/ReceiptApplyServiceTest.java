package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptPurposes;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptApplyServiceTest {

    private static final LongSupplier NEXT_ID = () -> 1L;
    private static final Long SOURCE_STATEMENT_ID = 88L;

    @Mock
    private ReceiptAllocationService receiptAllocationService;

    @Mock
    private ReceiptSettlementSyncService settlementSyncService;

    @Mock
    private ReceiptPartyIdentityResolver partyIdentityResolver;

    private ReceiptApplyService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptApplyService(receiptAllocationService, settlementSyncService, partyIdentityResolver);
    }

    private ReceiptRequest customerRequest(String status, BigDecimal amount,
                                           Long sourceStatementId, List<ReceiptAllocationRequest> items) {
        return new ReceiptRequest(
                "RC001", "客户", null, "C001", "客户A",
                ReceiptPurposes.CUSTOMER_STATEMENT_SETTLEMENT, 10L, "C001", "客户A",
                null, null, 1L, "主体A",
                7L, sourceStatementId,
                LocalDate.of(2026, 9, 1), "银行转账", amount, status, "操作员", "备注",
                items, false);
    }

    private ReceiptRequest supplierRequest(String status, BigDecimal amount,
                                           Long sourceStatementId, List<ReceiptAllocationRequest> items) {
        return new ReceiptRequest(
                "RC001", "供应商", 5L, "S001", "供应商A",
                ReceiptPurposes.SUPPLIER_OTHER_RECEIPT, null, null, null,
                null, null, 1L, "主体A",
                7L, sourceStatementId,
                LocalDate.of(2026, 9, 1), "银行转账", amount, status, "操作员", "备注",
                items, false);
    }

    private static final ReceiptPartyIdentityResolver.PartySnapshot CUSTOMER_SNAPSHOT =
            new ReceiptPartyIdentityResolver.PartySnapshot(10L, "C001", "客户A", null, null, 1L, "主体A");

    private static final ReceiptPartyIdentityResolver.SupplierPartySnapshot SUPPLIER_SNAPSHOT =
            new ReceiptPartyIdentityResolver.SupplierPartySnapshot(5L, "S001", "供应商A", 1L, "主体A");

    private ReceiptAllocationService.AllocationApplyResult emptyAllocation() {
        return new ReceiptAllocationService.AllocationApplyResult(
                null, null, null, null, null, BigDecimal.ZERO, true);
    }

    private ReceiptAllocationService.AllocationApplyResult allocation(Long customerId, Long projectId,
                                                                     String customerCode, BigDecimal total) {
        return new ReceiptAllocationService.AllocationApplyResult(
                customerId, projectId, customerCode, 1L, "主体A", total, false);
    }

    @Test
    void apply_shouldRejectZeroAmount() {
        Receipt entity = new Receipt();

        assertThatThrownBy(() -> service.apply(entity, customerRequest(StatusConstants.DRAFT, BigDecimal.ZERO, null, null), NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款金额必须大于0");

        verify(receiptAllocationService, never()).applyAllocations(any(), any(), any(), any());
    }

    @Test
    void apply_shouldRejectNegativeAmount() {
        Receipt entity = new Receipt();

        assertThatThrownBy(() -> service.apply(entity, customerRequest(StatusConstants.DRAFT, new BigDecimal("-1.00"), null, null), NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款金额必须大于0");
    }

    @Test
    void apply_shouldRejectNullAmount() {
        Receipt entity = new Receipt();

        assertThatThrownBy(() -> service.apply(entity, customerRequest(StatusConstants.DRAFT, null, null, null), NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款金额必须大于0");
    }

    @Test
    void apply_shouldRejectInvalidPurpose() {
        Receipt entity = new Receipt();
        ReceiptRequest request = new ReceiptRequest(
                "RC001", "客户", null, "C001", "客户A",
                "NOT_A_PURPOSE", 10L, "C001", "客户A",
                null, null, 1L, "主体A",
                7L, null,
                LocalDate.of(2026, 9, 1), "银行转账", new BigDecimal("100.00"), StatusConstants.DRAFT,
                "操作员", "备注", null, false);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款用途不合法");
    }

    @Test
    void apply_shouldRejectInvalidStatus() {
        Receipt entity = new Receipt();

        assertThatThrownBy(() -> service.apply(entity, customerRequest(StatusConstants.COMPLETED, new BigDecimal("100.00"), null, null), NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款单状态不合法");
    }

    @Test
    void apply_shouldRejectNonDraftStatusOnCreate() {
        Receipt entity = new Receipt();
        when(partyIdentityResolver.resolveSupplier(any())).thenReturn(SUPPLIER_SNAPSHOT);

        assertThatThrownBy(() -> service.apply(entity, supplierRequest(StatusConstants.AUDITED, new BigDecimal("100.00"), null, null), NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新建收款单只能保存为草稿");
    }

    @Test
    void apply_shouldRejectStatusChangeOnExistingEntity() {
        Receipt entity = new Receipt();
        entity.setStatus(StatusConstants.DRAFT);
        when(partyIdentityResolver.resolveSupplier(any())).thenReturn(SUPPLIER_SNAPSHOT);

        assertThatThrownBy(() -> service.apply(entity, supplierRequest(StatusConstants.AUDITED, new BigDecimal("100.00"), null, null), NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("普通保存不能修改收款单状态");
    }

    @Test
    void apply_shouldRejectCustomerReceiptWithNonCustomerCounterpartyType() {
        Receipt entity = new Receipt();
        ReceiptRequest request = new ReceiptRequest(
                "RC001", "供应商", 5L, "S001", "供应商A",
                ReceiptPurposes.CUSTOMER_STATEMENT_SETTLEMENT, 10L, "C001", "客户A",
                null, null, 1L, "主体A",
                7L, null,
                LocalDate.of(2026, 9, 1), "银行转账", new BigDecimal("100.00"), StatusConstants.DRAFT,
                "操作员", "备注", null, false);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户收款的往来类型必须为客户");
    }

    @Test
    void apply_shouldRejectSupplierReceiptWithNonSupplierCounterpartyType() {
        Receipt entity = new Receipt();
        ReceiptRequest request = new ReceiptRequest(
                "RC001", "客户", 5L, "S001", "供应商A",
                ReceiptPurposes.SUPPLIER_OTHER_RECEIPT, null, null, null,
                null, null, 1L, "主体A",
                7L, null,
                LocalDate.of(2026, 9, 1), "银行转账", new BigDecimal("100.00"), StatusConstants.DRAFT,
                "操作员", "备注", null, false);

        assertThatThrownBy(() -> service.apply(entity, request, NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商资金收款的往来类型必须为供应商");
    }

    @Test
    void apply_shouldRejectSupplierReceiptBoundToStatement() {
        Receipt entity = new Receipt();

        assertThatThrownBy(() -> service.apply(entity, supplierRequest(StatusConstants.DRAFT, new BigDecimal("100.00"), SOURCE_STATEMENT_ID, null), NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商资金收款不能关联客户对账单");
    }

    @Test
    void apply_shouldRejectSupplierReceiptWithAllocationItems() {
        Receipt entity = new Receipt();
        List<ReceiptAllocationRequest> items =
                List.of(new ReceiptAllocationRequest(null, SOURCE_STATEMENT_ID, new BigDecimal("100.00")));

        assertThatThrownBy(() -> service.apply(entity, supplierRequest(StatusConstants.DRAFT, new BigDecimal("100.00"), null, items), NEXT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商资金收款不能关联客户对账单");
    }

    @Test
    void apply_shouldUsePartyResolverWhenCustomerAllocationEmpty() {
        Receipt entity = new Receipt();
        ReceiptRequest request = customerRequest(StatusConstants.DRAFT, new BigDecimal("100.00"), null, null);
        when(receiptAllocationService.applyAllocations(entity, request, StatusConstants.DRAFT, NEXT_ID))
                .thenReturn(emptyAllocation());
        when(partyIdentityResolver.resolve(request)).thenReturn(CUSTOMER_SNAPSHOT);
        when(receiptAllocationService.mergeCustomerCode("C001", null)).thenReturn("C001");

        service.apply(entity, request, NEXT_ID);

        assertThat(entity.getReceiptPurpose()).isEqualTo(ReceiptPurposes.CUSTOMER_STATEMENT_SETTLEMENT);
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
        assertThat(entity.getCounterpartyType()).isEqualTo("客户");
        assertThat(entity.getCustomerId()).isEqualTo(10L);
        assertThat(entity.getCustomerName()).isEqualTo("客户A");
        assertThat(entity.getCustomerCode()).isEqualTo("C001");
        assertThat(entity.getSettlementCompanyId()).isEqualTo(1L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("主体A");
        assertThat(entity.getAmount()).isEqualByComparingTo("100.00");
        verify(settlementSyncService).captureOriginalAllocationStatementIds(entity);
    }

    @Test
    void apply_shouldPreferAllocationResultOverPartyResolver() {
        Receipt entity = new Receipt();
        ReceiptRequest request = customerRequest(StatusConstants.DRAFT, new BigDecimal("100.00"), null, null);
        when(receiptAllocationService.applyAllocations(entity, request, StatusConstants.DRAFT, NEXT_ID))
                .thenReturn(allocation(30L, 40L, "C999", new BigDecimal("80.00")));
        when(receiptAllocationService.mergeCustomerCode("C001", "C999")).thenReturn("C999");
        when(settlementSyncService.resolveLegacySourceStatementId(entity)).thenReturn(SOURCE_STATEMENT_ID);

        service.apply(entity, request, NEXT_ID);

        assertThat(entity.getCustomerId()).isEqualTo(30L);
        assertThat(entity.getProjectId()).isEqualTo(40L);
        assertThat(entity.getCustomerCode()).isEqualTo("C999");
        assertThat(entity.getSourceStatementId()).isEqualTo(SOURCE_STATEMENT_ID);
        verify(partyIdentityResolver, never()).resolve(any());
    }

    @Test
    void apply_shouldScaleAmountToTwoDecimals() {
        Receipt entity = new Receipt();
        ReceiptRequest request = customerRequest(StatusConstants.DRAFT, new BigDecimal("100.555"), null, null);
        when(receiptAllocationService.applyAllocations(entity, request, StatusConstants.DRAFT, NEXT_ID))
                .thenReturn(allocation(10L, null, "C001", new BigDecimal("80.00")));

        service.apply(entity, request, NEXT_ID);

        assertThat(entity.getAmount()).isEqualByComparingTo("100.56");
        assertThat(entity.getAmount().scale()).isEqualTo(2);
    }

    @Test
    void apply_shouldDefaultBlankPurposeAndStatus() {
        Receipt entity = new Receipt();
        ReceiptRequest request = new ReceiptRequest(
                "RC001", "客户", null, "C001", "客户A",
                "   ", 10L, "C001", "客户A",
                null, null, 1L, "主体A",
                7L, null,
                LocalDate.of(2026, 9, 1), "银行转账", new BigDecimal("100.00"), "  ",
                "操作员", "备注", null, false);
        when(receiptAllocationService.applyAllocations(entity, request, StatusConstants.DRAFT, NEXT_ID))
                .thenReturn(emptyAllocation());
        when(partyIdentityResolver.resolve(request)).thenReturn(CUSTOMER_SNAPSHOT);

        service.apply(entity, request, NEXT_ID);

        assertThat(entity.getReceiptPurpose()).isEqualTo(ReceiptPurposes.CUSTOMER_STATEMENT_SETTLEMENT);
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void apply_shouldClearCustomerFieldsOnSupplierReceipt() {
        Receipt entity = new Receipt();
        entity.setCustomerId(99L);
        entity.setCustomerCode("OLD");
        entity.setCustomerName("旧客户");
        entity.setProjectId(98L);
        entity.setProjectName("旧项目");
        entity.setSourceStatementId(SOURCE_STATEMENT_ID);
        ReceiptRequest request = supplierRequest(StatusConstants.DRAFT, new BigDecimal("200.00"), null, null);
        when(partyIdentityResolver.resolveSupplier(request)).thenReturn(SUPPLIER_SNAPSHOT);

        service.apply(entity, request, NEXT_ID);

        assertThat(entity.getReceiptPurpose()).isEqualTo(ReceiptPurposes.SUPPLIER_OTHER_RECEIPT);
        assertThat(entity.getCounterpartyType()).isEqualTo("供应商");
        assertThat(entity.getCounterpartyId()).isEqualTo(5L);
        assertThat(entity.getCounterpartyCode()).isEqualTo("S001");
        assertThat(entity.getCounterpartyName()).isEqualTo("供应商A");
        assertThat(entity.getCustomerId()).isNull();
        assertThat(entity.getCustomerCode()).isNull();
        assertThat(entity.getCustomerName()).isNull();
        assertThat(entity.getProjectId()).isNull();
        assertThat(entity.getProjectName()).isNull();
        assertThat(entity.getSourceStatementId()).isNull();
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
        assertThat(entity.getAmount()).isEqualByComparingTo("200.00");
        verify(receiptAllocationService, never()).applyAllocations(any(), any(), any(), any());
        verify(settlementSyncService, never()).resolveLegacySourceStatementId(any());
    }
}
