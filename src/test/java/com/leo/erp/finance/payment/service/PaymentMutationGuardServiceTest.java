package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.common.service.SupplierLedgerLockService;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static com.leo.erp.common.support.StatusConstants.AUDITED;
import static com.leo.erp.common.support.StatusConstants.DRAFT;
import static com.leo.erp.common.support.StatusConstants.LEGACY_PAID;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentMutationGuardServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private SupplierLedgerLockService supplierLedgerLockService;

    @Mock
    private PaymentSettlementSyncService settlementSyncService;

    @Mock
    private PaymentAllocationService paymentAllocationService;

    @Mock
    private PaymentPurchasePrepaymentService purchasePrepaymentService;

    @Test
    void lockRoot_shouldDelegateToRepositoryForUpdate() {
        service().lockRoot(1L);
        verify(paymentRepository).findByIdAndDeletedFlagFalseForUpdate(1L);
    }

    @Test
    void assertUpdateAllowed_shouldRejectAuditedPayment() {
        Payment payment = payment(DRAFT, "SUPPLIER_PAYMENT", "供应商");
        payment.setStatus(AUDITED);

        assertThatThrownBy(() -> service().assertUpdateAllowed(payment, "修改"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已审核付款单禁止修改");
    }

    @Test
    void assertUpdateAllowed_shouldRejectLegacyPurchasePrepayment() {
        Payment payment = payment(DRAFT, "PURCHASE_PREPAYMENT", "供应商");

        assertThatThrownBy(() -> service().assertUpdateAllowed(payment, "修改"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("旧采购预付款及供应商对账付款仅供历史查询");
    }

    @Test
    void assertUpdateAllowed_shouldRejectLegacySupplierStatementSettlement() {
        Payment payment = payment(DRAFT, "STATEMENT_SETTLEMENT", "供应商");
        payment.setBusinessType("供应商");

        assertThatThrownBy(() -> service().assertUpdateAllowed(payment, "修改"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("旧采购预付款及供应商对账付款仅供历史查询");
    }

    @Test
    void assertUpdateAllowed_shouldAllowNormalDraftPayment() {
        Payment payment = payment(DRAFT, "SUPPLIER_PAYMENT", "供应商");

        assertThatCode(() -> service().assertUpdateAllowed(payment, "修改"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertStatusTransitionAllowed_shouldRejectUnAuditingAuditedPayment() {
        Payment payment = payment(AUDITED, "SUPPLIER_PAYMENT", "供应商");

        assertThatThrownBy(() -> service().assertStatusTransitionAllowed(payment, AUDITED, DRAFT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已审核付款单禁止反审核");
    }

    @Test
    void assertStatusTransitionAllowed_shouldLockSupplierLedgerOnlyForSupplierTotalPayment() {
        Payment payment = payment(DRAFT, "SUPPLIER_PAYMENT", "供应商");
        payment.setCounterpartyId(11L);
        payment.setSettlementCompanyId(30L);

        service().assertStatusTransitionAllowed(payment, DRAFT, AUDITED);

        verify(supplierLedgerLockService).lock(30L, 11L);
        verify(settlementSyncService, never()).captureOriginalAllocationState(payment);
        verify(paymentAllocationService, never()).validateExistingAllocationsForSettlement(payment, AUDITED);
    }

    @Test
    void assertStatusTransitionAllowed_shouldCaptureStateAndValidateForFreightPayment() {
        Payment payment = payment(DRAFT, "STATEMENT_SETTLEMENT", "物流商");
        payment.setBusinessType("物流商");
        payment.setCounterpartyId(11L);
        payment.setSettlementCompanyId(30L);

        service().assertStatusTransitionAllowed(payment, DRAFT, AUDITED);

        verify(supplierLedgerLockService, never()).lock(30L, 11L);
        var inOrder = org.mockito.Mockito.inOrder(settlementSyncService, paymentAllocationService);
        inOrder.verify(settlementSyncService).captureOriginalAllocationState(payment);
        inOrder.verify(paymentAllocationService).validateExistingAllocationsForSettlement(payment, AUDITED);
    }

    @Test
    void assertStatusTransitionAllowed_shouldRejectSupplierPaymentWithoutIdentity() {
        Payment payment = payment(DRAFT, "SUPPLIER_PAYMENT", "供应商");
        payment.setCounterpartyId(null);

        assertThatThrownBy(() -> service().assertStatusTransitionAllowed(payment, DRAFT, AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商付款缺少供应商或结算主体身份");
    }

    @Test
    void assertStatusTransitionAllowed_shouldRejectFreightPaymentWithoutIdentity() {
        Payment payment = payment(DRAFT, "SUPPLIER_PAYMENT", "物流商");
        payment.setCounterpartyId(null);
        payment.setSettlementCompanyId(null);

        assertThatThrownBy(() -> service().assertStatusTransitionAllowed(payment, DRAFT, AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流付款缺少物流商或结算主体身份");
    }

    @Test
    void assertStatusTransitionAllowed_shouldCaptureStateBeforeValidation() {
        Payment payment = payment(DRAFT, "STATEMENT_SETTLEMENT", "物流商");
        payment.setBusinessType("物流商");
        payment.setCounterpartyId(11L);
        payment.setSettlementCompanyId(30L);

        service().assertStatusTransitionAllowed(payment, DRAFT, AUDITED);

        var inOrder = org.mockito.Mockito.inOrder(settlementSyncService, paymentAllocationService);
        inOrder.verify(settlementSyncService).captureOriginalAllocationState(payment);
        inOrder.verify(paymentAllocationService).validateExistingAllocationsForSettlement(payment, AUDITED);
    }

    @Test
    void assertDeletable_shouldRejectAuditedPayment() {
        Payment payment = payment(AUDITED, "SUPPLIER_PAYMENT", "供应商");

        assertThatThrownBy(() -> service().assertDeletable(payment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已审核付款单禁止删除");
    }

    @Test
    void assertDeletable_shouldRejectLegacyPaidPayment() {
        Payment payment = payment(LEGACY_PAID, "SUPPLIER_PAYMENT", "供应商");

        assertThatThrownBy(() -> service().assertDeletable(payment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("历史已付款单据仅供查询");
    }

    @Test
    void assertDeletable_shouldRejectLegacyPurchasePrepaymentWithoutStatementLock() {
        Payment payment = payment(DRAFT, "PURCHASE_PREPAYMENT", "供应商");

        assertThatThrownBy(() -> service().assertDeletable(payment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("旧采购预付款及供应商对账付款仅供历史查询");

        verify(purchasePrepaymentService, never()).validateNoStatementAllocations(payment);
        verify(sourceAllocationLockService, never()).lockStatementSources(anyList(), anyList());
    }

    @Test
    void assertDeletable_shouldLockStatementsForFreightStatementSettlement() {
        Payment payment = payment(DRAFT, "STATEMENT_SETTLEMENT", "物流商");
        payment.setBusinessType("物流商");
        payment.setSourceStatementId(501L);

        service().assertDeletable(payment);

        verify(sourceAllocationLockService).lockStatementSources(List.of(), List.of(501L));
    }

    @Test
    void lockAllocationStatements_shouldCollectSortedDistinctFreightStatementIds() {
        Payment payment = payment(DRAFT, "STATEMENT_SETTLEMENT", "物流商");
        payment.setBusinessType("物流商");
        payment.setItems(List.of(
                allocation(502L, 502L),
                allocation(501L, null),
                allocation(null, 501L)
        ));
        PaymentRequest request = request("物流商", List.of(
                new PaymentAllocationRequest(1L, 503L, 503L, new BigDecimal("1.00")),
                new PaymentAllocationRequest(2L, 503L, null, new BigDecimal("1.00"))
        ));

        service().lockAllocationStatements(payment, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(sourceAllocationLockService).lockStatementSources(anyList(), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue()).containsExactly(501L, 502L, 503L);
    }

    @Test
    void lockAllocationStatements_shouldSkipPrepaymentAndSupplierTotalPayment() {
        Payment payment = payment(DRAFT, "PURCHASE_PREPAYMENT", "物流商");
        payment.setBusinessType("物流商");
        payment.setSourceStatementId(501L);

        service().lockAllocationStatements(payment, null);

        verify(sourceAllocationLockService).lockStatementSources(List.of(), List.of());
    }

    @Test
    void lockAllocationStatements_shouldHandleNullEntityAndRequest() {
        assertThatCode(() -> service().lockAllocationStatements(null, null))
                .doesNotThrowAnyException();
        verify(sourceAllocationLockService).lockStatementSources(List.of(), List.of());
    }

    @Test
    void lockAllocationStatements_shouldUseSourceStatementIdWhenNoItems() {
        Payment payment = payment(DRAFT, "STATEMENT_SETTLEMENT", "物流商");
        payment.setBusinessType("物流商");
        payment.setItems(List.of());
        payment.setSourceStatementId(501L);

        service().lockAllocationStatements(payment, null);

        verify(sourceAllocationLockService).lockStatementSources(List.of(), List.of(501L));
    }

    private PaymentMutationGuardService service() {
        return new PaymentMutationGuardService(
                paymentRepository,
                sourceAllocationLockService,
                supplierLedgerLockService,
                settlementSyncService,
                paymentAllocationService,
                purchasePrepaymentService
        );
    }

    private Payment payment(String status, String paymentPurpose, String counterpartyType) {
        Payment payment = new Payment();
        payment.setStatus(status);
        payment.setPaymentPurpose(paymentPurpose);
        payment.setCounterpartyType(counterpartyType);
        payment.setBusinessType("供应商");
        return payment;
    }

    private PaymentAllocation allocation(Long sourceStatementId, Long sourceFreightStatementId) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setSourceStatementId(sourceStatementId);
        allocation.setSourceFreightStatementId(sourceFreightStatementId);
        return allocation;
    }

    private PaymentRequest request(String businessType, List<PaymentAllocationRequest> items) {
        return new PaymentRequest(
                "PAY001",
                businessType,
                11L,
                "STATEMENT_SETTLEMENT",
                "SUP001",
                "供应商A",
                null,
                null,
                null,
                "SUP001",
                "供应商A",
                30L,
                "结算主体",
                null,
                java.time.LocalDate.of(2026, 8, 25),
                "银行转账",
                new BigDecimal("10.00"),
                DRAFT,
                "操作员",
                null,
                items,
                false
        );
    }
}
