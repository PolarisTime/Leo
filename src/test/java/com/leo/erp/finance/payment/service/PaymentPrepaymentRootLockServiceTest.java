package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.mapper.PaymentMapper;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentPrepaymentRootLockServiceTest {

    @Test
    void shouldLockPaymentRootBeforeReverseAuditingPurchasePrepayment() {
        Fixture fixture = new Fixture(StatusConstants.PAID);

        fixture.service.updateStatus(5L, StatusConstants.DRAFT);

        InOrder flow = inOrder(fixture.paymentRepository, fixture.prepaymentService);
        flow.verify(fixture.paymentRepository).findByIdAndDeletedFlagFalseForUpdate(5L);
        flow.verify(fixture.paymentRepository).findByIdAndDeletedFlagFalse(5L);
        flow.verify(fixture.prepaymentService).assertRefundLifecycleMutable(fixture.payment, "反审核");
        flow.verify(fixture.prepaymentService).validateNoStatementAllocations(fixture.payment);
    }

    @Test
    void shouldLockPaymentRootBeforeDeletingPurchasePrepayment() {
        Fixture fixture = new Fixture(StatusConstants.DRAFT);

        fixture.service.delete(5L);

        InOrder flow = inOrder(fixture.paymentRepository, fixture.prepaymentService);
        flow.verify(fixture.paymentRepository).findByIdAndDeletedFlagFalseForUpdate(5L);
        flow.verify(fixture.paymentRepository).findByIdAndDeletedFlagFalse(5L);
        flow.verify(fixture.prepaymentService).assertRefundLifecycleMutable(fixture.payment, "删除");
        flow.verify(fixture.prepaymentService).validateNoStatementAllocations(fixture.payment);
    }

    private static final class Fixture {
        private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        private final PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        private final Payment payment;
        private final PaymentService service;

        private Fixture(String status) {
            payment = payment(status);
            PaymentMapper paymentMapper = mock(PaymentMapper.class);
            when(paymentRepository.findByIdAndDeletedFlagFalseForUpdate(5L)).thenReturn(Optional.of(payment));
            when(paymentRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(paymentMapper.toResponse(any(Payment.class))).thenReturn(mock(PaymentResponse.class));
            service = new PaymentService(
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
        }

        private static Payment payment(String status) {
            Payment payment = new Payment();
            payment.setId(5L);
            payment.setPaymentNo("FK-005");
            payment.setBusinessType("供应商");
            payment.setPaymentPurpose(PaymentPurposes.PURCHASE_PREPAYMENT);
            payment.setSourcePurchaseOrderId(9L);
            payment.setAmount(new BigDecimal("70.00"));
            payment.setStatus(status);
            payment.setItems(new ArrayList<>());
            return payment;
        }
    }
}
