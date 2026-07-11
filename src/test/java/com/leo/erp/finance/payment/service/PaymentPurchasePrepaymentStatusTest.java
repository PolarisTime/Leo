package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.mapper.PaymentMapper;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentPurchasePrepaymentStatusTest {

    @Test
    void shouldRecheckPurchasePrepaymentCapacityBeforeChangingDraftToPaid() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        Payment payment = payment();
        Payment existing = org.mockito.Mockito.spy(payment);
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

        service.updateStatus(5L, StatusConstants.PAID);

        InOrder flow = inOrder(prepaymentService, existing);
        flow.verify(prepaymentService).applySourceSnapshot(
                existing,
                9L,
                new BigDecimal("70.00"),
                StatusConstants.PAID
        );
        flow.verify(existing).setStatus(StatusConstants.PAID);
        verify(allocationService, never()).validateExistingAllocationsForSettlement(any(), any());
        verify(lockService, never()).lockStatementSources(any(), any(), any());
    }

    @Test
    void shouldAllowPaidPrepaymentToReturnToDraftWithoutReloadingSource() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        Payment existing = payment();
        existing.setStatus(StatusConstants.PAID);
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

        service.updateStatus(5L, StatusConstants.DRAFT);

        verify(prepaymentService).assertRefundLifecycleMutable(existing, "反审核");
        verify(prepaymentService).validateNoStatementAllocations(existing);
        verify(prepaymentService, never()).applySourceSnapshot(any(), any(), any(), any());
    }

    @Test
    void shouldRejectDeletingPrepaymentWhenRefundLifecycleExists() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        Payment existing = payment();
        doThrow(new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "采购预付款来源采购订单已存在采购退款单，不能删除"
        )).when(prepaymentService).assertRefundLifecycleMutable(existing, "删除");
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
                .hasMessageContaining("采购退款单")
                .hasMessageContaining("不能删除");

        verify(paymentRepository, never()).save(any());
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setPaymentNo("FK-005");
        payment.setBusinessType("供应商");
        payment.setPaymentPurpose(PaymentPurposes.PURCHASE_PREPAYMENT);
        payment.setSourcePurchaseOrderId(9L);
        payment.setAmount(new BigDecimal("70.00"));
        payment.setStatus(StatusConstants.DRAFT);
        payment.setItems(new ArrayList<>());
        return payment;
    }
}
