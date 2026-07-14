package com.leo.erp.finance.supplierrefundreceipt.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.supplierrefundreceipt.domain.entity.SupplierRefundReceipt;
import com.leo.erp.finance.supplierrefundreceipt.mapper.SupplierRefundReceiptMapper;
import com.leo.erp.finance.supplierrefundreceipt.repository.SupplierRefundReceiptRepository;
import com.leo.erp.finance.supplierrefundreceipt.web.dto.SupplierRefundReceiptRequest;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import com.leo.erp.purchase.refund.domain.entity.PurchaseRefund;
import com.leo.erp.purchase.refund.repository.PurchaseRefundRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierRefundReceiptServiceTest {

    @Test
    void shouldLockPurchaseOrderSourceAndDeriveSupplierSnapshotFromAuditedRefund() {
        Fixture fixture = fixture();
        PurchaseRefund refund = auditedRefund("100.00");
        when(fixture.purchaseRefundRepository.findByIdAndDeletedFlagFalse(91L))
                .thenReturn(Optional.of(refund));
        when(fixture.repository.existsByRefundReceiptNoAndDeletedFlagFalse("SRR-001")).thenReturn(false);
        when(fixture.repository.save(any(SupplierRefundReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.create(request("30.00", StatusConstants.DRAFT));

        InOrder flow = inOrder(
                fixture.purchaseRefundRepository,
                fixture.purchaseOrderItemRepository,
                fixture.lockService,
                fixture.repository
        );
        flow.verify(fixture.purchaseRefundRepository).findByIdAndDeletedFlagFalse(91L);
        flow.verify(fixture.purchaseOrderItemRepository).findActiveIdsByPurchaseOrderId(11L);
        flow.verify(fixture.lockService).lockTradeItemSources(List.of(111L, 112L), List.of(), List.of());
        flow.verify(fixture.purchaseRefundRepository).findByIdAndDeletedFlagFalse(91L);
        flow.verify(fixture.repository).save(any(SupplierRefundReceipt.class));

        ArgumentCaptor<SupplierRefundReceipt> receiptCaptor = ArgumentCaptor.forClass(SupplierRefundReceipt.class);
        verify(fixture.repository).save(receiptCaptor.capture());
        SupplierRefundReceipt saved = receiptCaptor.getValue();
        assertThat(saved.getPurchaseRefundId()).isEqualTo(91L);
        assertThat(saved.getSupplierId()).isEqualTo(81L);
        assertThat(saved.getSupplierCode()).isEqualTo("SUP-001");
        assertThat(saved.getSupplierName()).isEqualTo("供应商A");
        assertThat(saved.getSettlementCompanyId()).isEqualTo(21L);
        assertThat(saved.getSettlementCompanyName()).isEqualTo("结算主体A");
        assertThat(saved.getAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void shouldRejectRequestedSupplierIdThatConflictsWithPurchaseRefund() {
        Fixture fixture = fixture();
        when(fixture.purchaseRefundRepository.findByIdAndDeletedFlagFalse(91L))
                .thenReturn(Optional.of(auditedRefund("100.00")));
        when(fixture.repository.existsByRefundReceiptNoAndDeletedFlagFalse("SRR-001")).thenReturn(false);
        SupplierRefundReceiptRequest request = new SupplierRefundReceiptRequest(
                "SRR-001",
                91L,
                82L,
                LocalDate.of(2026, 7, 11),
                "银行转账",
                new BigDecimal("30.00"),
                StatusConstants.DRAFT,
                "财务A",
                null
        );

        assertThatThrownBy(() -> fixture.service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商ID与采购退款单不一致");

        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldAllowInstallmentReceiptWhenReceivedTotalDoesNotExceedRefund() {
        Fixture fixture = fixture();
        SupplierRefundReceipt receipt = draftReceipt("30.00");
        when(fixture.repository.findByIdAndDeletedFlagFalse(501L)).thenReturn(Optional.of(receipt));
        when(fixture.purchaseRefundRepository.findByIdAndDeletedFlagFalse(91L))
                .thenReturn(Optional.of(auditedRefund("100.00")));
        when(fixture.repository.sumReceivedAmountByPurchaseRefundIdExcludingReceiptId(91L, 501L))
                .thenReturn(new BigDecimal("70.00"));
        when(fixture.repository.save(receipt)).thenReturn(receipt);

        fixture.service.updateStatus(501L, StatusConstants.RECEIVED);

        assertThat(receipt.getStatus()).isEqualTo(StatusConstants.RECEIVED);
        verify(fixture.repository).save(receipt);
    }

    @Test
    void shouldRejectReceivingWhenInstallmentsExceedRefundAmount() {
        Fixture fixture = fixture();
        SupplierRefundReceipt receipt = draftReceipt("30.01");
        when(fixture.repository.findByIdAndDeletedFlagFalse(501L)).thenReturn(Optional.of(receipt));
        when(fixture.purchaseRefundRepository.findByIdAndDeletedFlagFalse(91L))
                .thenReturn(Optional.of(auditedRefund("100.00")));
        when(fixture.repository.sumReceivedAmountByPurchaseRefundIdExcludingReceiptId(91L, 501L))
                .thenReturn(new BigDecimal("70.00"));

        assertThatThrownBy(() -> fixture.service.updateStatus(501L, StatusConstants.RECEIVED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("累计到账金额")
                .hasMessageContaining("退款金额");

        assertThat(receipt.getStatus()).isEqualTo(StatusConstants.DRAFT);
        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldRejectStatusUpdateWhenReceiptSupplierIdConflictsWithSourceRefund() {
        Fixture fixture = fixture();
        SupplierRefundReceipt receipt = draftReceipt("30.00");
        receipt.setSupplierId(82L);
        when(fixture.repository.findByIdAndDeletedFlagFalse(501L)).thenReturn(Optional.of(receipt));
        when(fixture.purchaseRefundRepository.findByIdAndDeletedFlagFalse(91L))
                .thenReturn(Optional.of(auditedRefund("100.00")));
        when(fixture.repository.save(receipt)).thenReturn(receipt);

        assertThatThrownBy(() -> fixture.service.updateStatus(501L, StatusConstants.RECEIVED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商ID与采购退款单不一致");

        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldRejectStatusUpdateWhenReceiptSupplierIdIsMissing() {
        Fixture fixture = fixture();
        SupplierRefundReceipt receipt = draftReceipt("30.00");
        receipt.setSupplierId(null);
        when(fixture.repository.findByIdAndDeletedFlagFalse(501L)).thenReturn(Optional.of(receipt));
        when(fixture.purchaseRefundRepository.findByIdAndDeletedFlagFalse(91L))
                .thenReturn(Optional.of(auditedRefund("100.00")));
        when(fixture.repository.save(receipt)).thenReturn(receipt);

        assertThatThrownBy(() -> fixture.service.updateStatus(501L, StatusConstants.RECEIVED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退款到账单缺少供应商ID");

        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldRejectDeleteWhenReceiptSupplierIdConflictsWithSourceRefund() {
        Fixture fixture = fixture();
        SupplierRefundReceipt receipt = draftReceipt("30.00");
        receipt.setSupplierId(82L);
        when(fixture.repository.findByIdAndDeletedFlagFalse(501L)).thenReturn(Optional.of(receipt));
        when(fixture.purchaseRefundRepository.findByIdAndDeletedFlagFalse(91L))
                .thenReturn(Optional.of(auditedRefund("100.00")));
        when(fixture.repository.save(receipt)).thenReturn(receipt);

        assertThatThrownBy(() -> fixture.service.delete(501L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商ID与采购退款单不一致");

        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldRejectCreatingReceiptForUnauditedRefund() {
        Fixture fixture = fixture();
        PurchaseRefund refund = auditedRefund("100.00");
        refund.setStatus(StatusConstants.DRAFT);
        when(fixture.purchaseRefundRepository.findByIdAndDeletedFlagFalse(91L))
                .thenReturn(Optional.of(refund));
        when(fixture.repository.existsByRefundReceiptNoAndDeletedFlagFalse("SRR-001")).thenReturn(false);

        assertThatThrownBy(() -> fixture.service.create(request("30.00", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购退款单未审核");

        verify(fixture.lockService, never()).lockTradeItemSources(any(), any(), any());
        verify(fixture.repository, never()).save(any());
    }

    private Fixture fixture() {
        SupplierRefundReceiptRepository repository = mock(SupplierRefundReceiptRepository.class);
        PurchaseRefundRepository purchaseRefundRepository = mock(PurchaseRefundRepository.class);
        PurchaseOrderItemRepository purchaseOrderItemRepository = mock(PurchaseOrderItemRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        when(idGenerator.nextId()).thenReturn(501L, 502L);
        when(purchaseOrderItemRepository.findActiveIdsByPurchaseOrderId(11L))
                .thenReturn(List.of(111L, 112L));
        SupplierRefundReceiptService service = new SupplierRefundReceiptService(
                repository,
                purchaseRefundRepository,
                purchaseOrderItemRepository,
                idGenerator,
                lockService,
                new SupplierRefundReceiptMapper(),
                accessGuard,
                workflowTransitionGuard
        );
        return new Fixture(
                service,
                repository,
                purchaseRefundRepository,
                purchaseOrderItemRepository,
                lockService
        );
    }

    private SupplierRefundReceiptRequest request(String amount, String status) {
        return new SupplierRefundReceiptRequest(
                "SRR-001",
                91L,
                LocalDate.of(2026, 7, 11),
                "银行转账",
                new BigDecimal(amount),
                status,
                "财务A",
                "供应商退款到账"
        );
    }

    private PurchaseRefund auditedRefund(String amount) {
        PurchaseRefund refund = new PurchaseRefund();
        refund.setId(91L);
        refund.setRefundNo("PR-001");
        refund.setSourcePurchaseOrderId(11L);
        refund.setPurchaseOrderNo("PO-001");
        refund.setSupplierId(81L);
        refund.setSupplierCode("SUP-001");
        refund.setSupplierName("供应商A");
        refund.setSettlementCompanyId(21L);
        refund.setSettlementCompanyName("结算主体A");
        refund.setTotalAmount(new BigDecimal(amount));
        refund.setStatus(StatusConstants.AUDITED);
        return refund;
    }

    private SupplierRefundReceipt draftReceipt(String amount) {
        SupplierRefundReceipt receipt = new SupplierRefundReceipt();
        receipt.setId(501L);
        receipt.setRefundReceiptNo("SRR-001");
        receipt.setPurchaseRefundId(91L);
        receipt.setSupplierId(81L);
        receipt.setSupplierCode("SUP-001");
        receipt.setSupplierName("供应商A");
        receipt.setSettlementCompanyId(21L);
        receipt.setSettlementCompanyName("结算主体A");
        receipt.setReceiptDate(LocalDate.of(2026, 7, 11));
        receipt.setReceiptMethod("银行转账");
        receipt.setAmount(new BigDecimal(amount));
        receipt.setStatus(StatusConstants.DRAFT);
        receipt.setOperatorName("财务A");
        return receipt;
    }

    private record Fixture(
            SupplierRefundReceiptService service,
            SupplierRefundReceiptRepository repository,
            PurchaseRefundRepository purchaseRefundRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            SourceAllocationLockService lockService
    ) {
    }
}
