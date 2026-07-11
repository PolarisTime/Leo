package com.leo.erp.finance.payment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.freight.service.FreightStatementQueryService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentApplyServiceTest {

    @Test
    void shouldDefaultMissingPurposeToStatementSettlement() {
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        PaymentApplyService service = service(allocationService, prepaymentService);
        Payment entity = payment();
        entity.setSourcePurchaseOrderId(99L);
        entity.setPurchaseOrderNo("STALE");
        entity.setSupplierCode("STALE");
        entity.setSupplierName("STALE");
        entity.setSettlementCompanyId(99L);
        entity.setSettlementCompanyName("STALE");
        PaymentRequest request = legacyStatementRequest();
        when(allocationService.applyAllocations(any(), eq(request), eq(StatusConstants.DRAFT), any()))
                .thenReturn(new PaymentAllocationService.AllocationApplyResult(
                        null,
                        null,
                        null,
                        BigDecimal.ZERO,
                        true
                ));

        service.apply(entity, request, () -> 100L);

        assertThat(entity.getPaymentPurpose()).isEqualTo(PaymentPurposes.STATEMENT_SETTLEMENT);
        assertThat(entity.getSourcePurchaseOrderId()).isNull();
        assertThat(entity.getPurchaseOrderNo()).isNull();
        assertThat(entity.getSupplierCode()).isNull();
        assertThat(entity.getSupplierName()).isNull();
        assertThat(entity.getSettlementCompanyId()).isNull();
        assertThat(entity.getSettlementCompanyName()).isNull();
        verify(allocationService).applyAllocations(any(), eq(request), eq(StatusConstants.DRAFT), any());
        verify(prepaymentService, never()).applySourceSnapshot(any(), anyLong(), any(), any());
    }

    @Test
    void shouldKeepExplicitStatementSettlementBehavior() {
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        PaymentApplyService service = service(allocationService, prepaymentService);
        PaymentRequest request = request(
                PaymentPurposes.STATEMENT_SETTLEMENT,
                null,
                StatusConstants.DRAFT,
                List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("70.00")))
        );
        when(allocationService.applyAllocations(any(), eq(request), eq(StatusConstants.DRAFT), any()))
                .thenReturn(new PaymentAllocationService.AllocationApplyResult(
                        "SUP-001",
                        1001L,
                        "来源结算主体",
                        new BigDecimal("70.00"),
                        false
                ));

        Payment entity = payment();
        service.apply(entity, request, () -> 100L);

        assertThat(entity.getPaymentPurpose()).isEqualTo(PaymentPurposes.STATEMENT_SETTLEMENT);
        verify(allocationService).applyAllocations(any(), eq(request), eq(StatusConstants.DRAFT), any());
        verify(prepaymentService, never()).applySourceSnapshot(any(), anyLong(), any(), any());
    }

    @Test
    void shouldDeriveSettlementCompanySnapshotFromStatementAndIgnoreClientValues() {
        SupplierStatement statement = new SupplierStatement();
        statement.setId(11L);
        statement.setSupplierCode("SUP-001");
        statement.setSupplierName("供应商A");
        statement.setSettlementCompanyId(1001L);
        statement.setSettlementCompanyName("来源结算主体");
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        SupplierStatementQueryService supplierQueryService = mock(SupplierStatementQueryService.class);
        when(supplierQueryService.requireActiveById(11L)).thenReturn(statement);
        PaymentStatementAllocationValidator validator = new PaymentStatementAllocationValidator(
                mock(PaymentAllocationRepository.class),
                supplierQueryService,
                mock(FreightStatementQueryService.class),
                mock(ResourceRecordAccessGuard.class)
        );
        PaymentApplyService service = service(
                new PaymentAllocationService(validator),
                mock(PaymentPurchasePrepaymentService.class)
        );
        PaymentRequest request = new PaymentRequest(
                "FK-001",
                PaymentAllocationService.SUPPLIER_PAYMENT_TYPE,
                PaymentPurposes.STATEMENT_SETTLEMENT,
                "SUP-001",
                "供应商A",
                null,
                null,
                null,
                null,
                null,
                999L,
                "客户端伪造主体",
                LocalDate.of(2026, 7, 11),
                "银行转账",
                new BigDecimal("70.00"),
                StatusConstants.DRAFT,
                "财务A",
                null,
                List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("70.00")))
        );
        Payment entity = payment();

        service.apply(entity, request, () -> 100L);

        assertThat(entity.getSettlementCompanyId()).isEqualTo(1001L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("来源结算主体");
    }

    @Test
    void shouldIgnoreClientSnapshotsForPurchasePrepayment() {
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        PaymentApplyService service = service(allocationService, prepaymentService);
        PaymentRequest request = request(
                PaymentPurposes.PURCHASE_PREPAYMENT,
                9L,
                StatusConstants.DRAFT,
                List.of()
        );
        doAnswer(invocation -> {
            Payment entity = invocation.getArgument(0);
            entity.setSourcePurchaseOrderId(9L);
            entity.setPurchaseOrderNo("PO-009");
            entity.setSupplierCode("SUP-009");
            entity.setSupplierName("来源供应商");
            entity.setSettlementCompanyId(19L);
            entity.setSettlementCompanyName("来源结算主体");
            entity.setCounterpartyCode("SUP-009");
            entity.setCounterpartyName("来源供应商");
            return null;
        }).when(prepaymentService).applySourceSnapshot(
                any(Payment.class),
                eq(9L),
                eq(new BigDecimal("70.00")),
                eq(StatusConstants.DRAFT)
        );

        Payment entity = payment();
        service.apply(entity, request, () -> 100L);

        assertThat(entity.getPaymentPurpose()).isEqualTo(PaymentPurposes.PURCHASE_PREPAYMENT);
        assertThat(entity.getPurchaseOrderNo()).isEqualTo("PO-009");
        assertThat(entity.getSupplierCode()).isEqualTo("SUP-009");
        assertThat(entity.getSupplierName()).isEqualTo("来源供应商");
        assertThat(entity.getSettlementCompanyId()).isEqualTo(19L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("来源结算主体");
        assertThat(entity.getCounterpartyCode()).isEqualTo("SUP-009");
        assertThat(entity.getCounterpartyName()).isEqualTo("来源供应商");
        assertThat(entity.getSourceStatementId()).isNull();
        assertThat(entity.getItems()).isEmpty();
        verify(allocationService, never()).applyAllocations(any(), any(), any(), any());
    }

    @Test
    void shouldRejectStatementAllocationsForDraftAndPaidPurchasePrepayments() {
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        PaymentApplyService service = service(allocationService, prepaymentService);

        for (String status : List.of(StatusConstants.DRAFT, StatusConstants.PAID)) {
            PaymentRequest request = request(
                    PaymentPurposes.PURCHASE_PREPAYMENT,
                    9L,
                    status,
                    List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("70.00")))
            );

            assertThatThrownBy(() -> service.apply(payment(), request, () -> 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("采购预付款不能包含对账单核销明细");
        }

        verify(prepaymentService, never()).applySourceSnapshot(any(), anyLong(), any(), any());
        verify(allocationService, never()).applyAllocations(any(), any(), any(), any());
    }

    @Test
    void shouldRejectLegacyStatementSourceForPurchasePrepayment() {
        PaymentApplyService service = service(
                mock(PaymentAllocationService.class),
                mock(PaymentPurchasePrepaymentService.class)
        );
        PaymentRequest request = new PaymentRequest(
                "FK-001",
                "供应商",
                PaymentPurposes.PURCHASE_PREPAYMENT,
                "FAKE-CODE",
                "伪造供应商",
                11L,
                9L,
                "FAKE-ORDER",
                "FAKE-SUPPLIER",
                "伪造供应商",
                999L,
                "伪造结算主体",
                LocalDate.of(2026, 7, 11),
                "银行转账",
                new BigDecimal("70.00"),
                StatusConstants.DRAFT,
                "财务A",
                null,
                List.of()
        );

        assertThatThrownBy(() -> service.apply(payment(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款不能关联对账单");
    }

    @Test
    void shouldRejectNonPositivePurchasePrepaymentAmountBeforeLoadingSource() {
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        PaymentApplyService service = service(mock(PaymentAllocationService.class), prepaymentService);
        PaymentRequest source = request(
                PaymentPurposes.PURCHASE_PREPAYMENT,
                9L,
                StatusConstants.DRAFT,
                List.of()
        );
        PaymentRequest request = new PaymentRequest(
                source.paymentNo(),
                source.businessType(),
                source.paymentPurpose(),
                source.counterpartyCode(),
                source.counterpartyName(),
                source.sourceStatementId(),
                source.sourcePurchaseOrderId(),
                source.purchaseOrderNo(),
                source.supplierCode(),
                source.supplierName(),
                source.settlementCompanyId(),
                source.settlementCompanyName(),
                source.paymentDate(),
                source.payType(),
                BigDecimal.ZERO,
                source.status(),
                source.operatorName(),
                source.remark(),
                source.items()
        );

        assertThatThrownBy(() -> service.apply(payment(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款金额必须大于0");
        verify(prepaymentService, never()).applySourceSnapshot(any(), anyLong(), any(), any());
    }

    private PaymentApplyService service(PaymentAllocationService allocationService,
                                        PaymentPurchasePrepaymentService prepaymentService) {
        return new PaymentApplyService(
                mock(WorkflowTransitionGuard.class),
                allocationService,
                mock(PaymentSettlementSyncService.class),
                prepaymentService
        );
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setItems(new ArrayList<>());
        return payment;
    }

    private PaymentRequest legacyStatementRequest() {
        return new PaymentRequest(
                "FK-001",
                "供应商",
                "SUP-001",
                "供应商A",
                null,
                LocalDate.of(2026, 7, 11),
                "银行转账",
                new BigDecimal("70.00"),
                StatusConstants.DRAFT,
                "财务A",
                null,
                List.of()
        );
    }

    private PaymentRequest request(String paymentPurpose,
                                   Long sourcePurchaseOrderId,
                                   String status,
                                   List<PaymentAllocationRequest> items) {
        return new PaymentRequest(
                "FK-001",
                "供应商",
                paymentPurpose,
                "FAKE-CODE",
                "伪造供应商",
                null,
                sourcePurchaseOrderId,
                "FAKE-ORDER",
                "FAKE-SUPPLIER",
                "伪造供应商快照",
                999L,
                "伪造结算主体",
                LocalDate.of(2026, 7, 11),
                "银行转账",
                new BigDecimal("70.00"),
                status,
                "财务A",
                null,
                items
        );
    }
}
