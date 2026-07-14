package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.mapper.PaymentMapper;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentPurchasePrepaymentStatusTest {

    @Test
    void shouldRejectAuditingLegacyPurchasePrepayment() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        Payment payment = payment();
        Payment existing = payment;
        when(paymentRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(mock(com.leo.erp.finance.payment.web.dto.PaymentResponse.class));
        PaymentService service = new PaymentService(
                paymentRepository,
                mock(SnowflakeIdGenerator.class),
                paymentMapper,
                mock(PaymentApplyService.class),
                allocationService,
                mock(PaymentAllocationResponseAssembler.class),
                mock(PaymentSettlementSyncService.class),
                lockService,
                prepaymentService
        );
        service.setSupplierLedgerLockService(mock(
                com.leo.erp.finance.purchaseflow.service.SupplierLedgerLockService.class
        ));

        assertThatThrownBy(() -> service.updateStatus(5L, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅供历史查询")
                .hasMessageContaining("不允许审核");

        verify(prepaymentService, never()).applySourceSnapshot(any(), any(), any(), any());
        verify(allocationService, never()).validateExistingAllocationsForSettlement(any(), any());
        verify(lockService, never()).lockStatementSources(any(), any(), any());
    }

    @Test
    void shouldRejectAuditedPrepaymentReturningToDraft() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        Payment existing = payment();
        existing.setStatus(StatusConstants.AUDITED);
        when(paymentRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(
                mock(com.leo.erp.finance.payment.web.dto.PaymentResponse.class)
        );
        PaymentService service = new PaymentService(
                paymentRepository,
                mock(SnowflakeIdGenerator.class),
                paymentMapper,
                mock(PaymentApplyService.class),
                mock(PaymentAllocationService.class),
                mock(PaymentAllocationResponseAssembler.class),
                mock(PaymentSettlementSyncService.class),
                mock(SourceAllocationLockService.class),
                prepaymentService
        );

        assertThatThrownBy(() -> service.updateStatus(5L, StatusConstants.DRAFT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从");

        verify(prepaymentService, never()).applySourceSnapshot(any(), any(), any(), any());
    }

    @Test
    void shouldRejectDeletingLegacyPurchasePrepaymentBeforeRefundLifecycleChecks() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        Payment existing = payment();
        PaymentService service = new PaymentService(
                paymentRepository,
                mock(SnowflakeIdGenerator.class),
                mock(PaymentMapper.class),
                mock(PaymentApplyService.class),
                mock(PaymentAllocationService.class),
                mock(PaymentAllocationResponseAssembler.class),
                mock(PaymentSettlementSyncService.class),
                mock(SourceAllocationLockService.class),
                prepaymentService
        );

        assertThatThrownBy(() -> service.beforeDelete(existing))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅供历史查询")
                .hasMessageContaining("不允许删除");

        verify(paymentRepository, never()).save(any());
        verify(prepaymentService, never()).assertRefundLifecycleMutable(any(), any());
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setPaymentNo("FK-005");
        payment.setBusinessType("供应商");
        payment.setPaymentPurpose(PaymentPurposes.PURCHASE_PREPAYMENT);
        payment.setSourcePurchaseOrderId(9L);
        payment.setCounterpartyId(201L);
        payment.setSettlementCompanyId(31L);
        payment.setAmount(new BigDecimal("70.00"));
        payment.setStatus(StatusConstants.DRAFT);
        payment.setItems(new ArrayList<>());
        return payment;
    }
}
