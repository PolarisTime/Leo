package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.supplierrefundreceipt.repository.SupplierRefundReceiptRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.refund.repository.PurchaseRefundRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentPurchasePrepaymentServiceTest {

    @Test
    void shouldLockAndReloadSourceBeforeCheckingPaidCapacityAndApplyingSnapshots() {
        PurchaseOrderRepository orderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);
        PaymentPurchasePrepaymentService service = new PaymentPurchasePrepaymentService(
                orderRepository,
                itemRepository,
                paymentRepository,
                lockService,
                accessGuard
        );
        PurchaseOrder sourceOrder = sourceOrder();
        Payment payment = payment(5L);

        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(102L, 101L));
        when(orderRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(sourceOrder));
        when(paymentRepository.sumPaidPurchasePrepaymentAmountExcludingId(9L, 5L))
                .thenReturn(new BigDecimal("80.00"));

        service.applySourceSnapshot(
                payment,
                9L,
                new BigDecimal("70.00"),
                StatusConstants.PAID
        );

        InOrder flow = inOrder(itemRepository, lockService, orderRepository, paymentRepository);
        flow.verify(itemRepository).findActiveIdsByPurchaseOrderId(9L);
        flow.verify(lockService).lockTradeItemSources(List.of(101L, 102L), List.of(), List.of());
        flow.verify(orderRepository).findByIdAndDeletedFlagFalse(9L);
        flow.verify(paymentRepository).sumPaidPurchasePrepaymentAmountExcludingId(9L, 5L);
        assertThat(payment.getSourcePurchaseOrderId()).isEqualTo(9L);
        assertThat(payment.getPurchaseOrderNo()).isEqualTo("PO-009");
        assertThat(payment.getSupplierCode()).isEqualTo("SUP-009");
        assertThat(payment.getSupplierName()).isEqualTo("来源供应商");
        assertThat(payment.getSettlementCompanyId()).isEqualTo(19L);
        assertThat(payment.getSettlementCompanyName()).isEqualTo("结算主体A");
        assertThat(payment.getCounterpartyCode()).isEqualTo("SUP-009");
        assertThat(payment.getCounterpartyName()).isEqualTo("来源供应商");
    }

    @Test
    void shouldLockOldAndNewPurchaseOrderItemsBeforeCheckingUpdatedPaymentCapacity() {
        PurchaseOrderRepository orderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PaymentPurchasePrepaymentService service = new PaymentPurchasePrepaymentService(
                orderRepository,
                itemRepository,
                paymentRepository,
                lockService,
                mock(ResourceRecordAccessGuard.class)
        );
        Payment payment = payment(5L);
        payment.setPaymentPurpose(PaymentPurposes.PURCHASE_PREPAYMENT);
        payment.setSourcePurchaseOrderId(8L);
        when(itemRepository.findActiveIdsByPurchaseOrderId(8L)).thenReturn(List.of(82L, 81L));
        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(92L, 91L));
        when(orderRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(sourceOrder()));
        when(paymentRepository.sumPaidPurchasePrepaymentAmountExcludingId(9L, 5L))
                .thenReturn(BigDecimal.ZERO);

        service.applySourceSnapshot(payment, 9L, new BigDecimal("10.00"), StatusConstants.PAID);

        InOrder flow = inOrder(itemRepository, lockService, paymentRepository);
        flow.verify(itemRepository).findActiveIdsByPurchaseOrderId(8L);
        flow.verify(itemRepository).findActiveIdsByPurchaseOrderId(9L);
        flow.verify(lockService).lockTradeItemSources(
                List.of(81L, 82L, 91L, 92L),
                List.of(),
                List.of()
        );
        flow.verify(paymentRepository).sumPaidPurchasePrepaymentAmountExcludingId(9L, 5L);
    }

    @Test
    void shouldRejectOverpaymentWhenStoredOrderAndLineAmountsAreInflated() {
        PurchaseOrderRepository orderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentPurchasePrepaymentService service = service(orderRepository, itemRepository, paymentRepository);
        PurchaseOrder sourceOrder = sourceOrder();
        sourceOrder.setTotalAmount(new BigDecimal("999999.99"));
        sourceOrder.getItems().forEach(item -> item.setAmount(new BigDecimal("999999.99")));

        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(101L, 102L));
        when(orderRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(sourceOrder));
        when(paymentRepository.sumPaidPurchasePrepaymentAmountExcludingId(9L, null))
                .thenReturn(new BigDecimal("149.99"));

        Payment payment = payment(null);
        assertThatThrownBy(() -> service.applySourceSnapshot(
                payment,
                9L,
                new BigDecimal("0.02"),
                StatusConstants.PAID
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购订单原始金额");
        verify(paymentRepository).sumPaidPurchasePrepaymentAmountExcludingId(9L, null);
    }

    @Test
    void shouldRejectPaidAmountExceedingRebuiltOriginalOrderAmount() {
        PurchaseOrderRepository orderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentPurchasePrepaymentService service = service(orderRepository, itemRepository, paymentRepository);

        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(101L, 102L));
        when(orderRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(sourceOrder()));
        when(paymentRepository.sumPaidPurchasePrepaymentAmountExcludingId(9L, 5L))
                .thenReturn(new BigDecimal("80.00"));

        assertThatThrownBy(() -> service.applySourceSnapshot(
                payment(5L),
                9L,
                new BigDecimal("70.01"),
                StatusConstants.PAID
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购订单原始金额");
    }

    @Test
    void shouldRoundOriginalAmountPerPurchaseOrderLine() {
        PurchaseOrderRepository orderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentPurchasePrepaymentService service = service(orderRepository, itemRepository, paymentRepository);
        PurchaseOrder sourceOrder = sourceOrder();
        sourceOrder.setItems(new ArrayList<>(List.of(
                item(sourceOrder, 101L, 1, "0.00500000", "1.00"),
                item(sourceOrder, 102L, 1, "0.00500000", "1.00")
        )));

        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(101L, 102L));
        when(orderRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(sourceOrder));
        when(paymentRepository.sumPaidPurchasePrepaymentAmountExcludingId(9L, 5L))
                .thenReturn(BigDecimal.ZERO);

        service.applySourceSnapshot(payment(5L), 9L, new BigDecimal("0.02"), StatusConstants.PAID);

        verify(paymentRepository).sumPaidPurchasePrepaymentAmountExcludingId(9L, 5L);
    }

    @Test
    void shouldNotQueryPaidCapacityForDraftPrepayment() {
        PurchaseOrderRepository orderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentPurchasePrepaymentService service = service(orderRepository, itemRepository, paymentRepository);

        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(101L, 102L));
        when(orderRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(sourceOrder()));

        service.applySourceSnapshot(payment(5L), 9L, new BigDecimal("999.00"), StatusConstants.DRAFT);

        verify(paymentRepository, never()).sumPaidPurchasePrepaymentAmountExcludingId(9L, 5L);
    }

    @Test
    void shouldRejectDraftPurchaseOrderWhenCreatingDraftPrepayment() {
        PurchaseOrderRepository orderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        PaymentPurchasePrepaymentService service = service(
                orderRepository,
                itemRepository,
                mock(PaymentRepository.class)
        );
        PurchaseOrder sourceOrder = sourceOrder();
        sourceOrder.setStatus(StatusConstants.DRAFT);
        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(101L, 102L));
        when(orderRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(sourceOrder));

        assertThatThrownBy(() -> service.applySourceSnapshot(
                payment(5L),
                9L,
                new BigDecimal("10.00"),
                StatusConstants.DRAFT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购订单状态")
                .hasMessageContaining("已审核或完成采购");
    }

    @Test
    void shouldAllowCompletedPurchaseOrderWhenPayingPrepayment() {
        PurchaseOrderRepository orderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentPurchasePrepaymentService service = service(orderRepository, itemRepository, paymentRepository);
        PurchaseOrder sourceOrder = sourceOrder();
        sourceOrder.setStatus(StatusConstants.PURCHASE_COMPLETED);
        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(101L, 102L));
        when(orderRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(sourceOrder));
        when(paymentRepository.sumPaidPurchasePrepaymentAmountExcludingId(9L, 5L))
                .thenReturn(BigDecimal.ZERO);

        service.applySourceSnapshot(
                payment(5L),
                9L,
                new BigDecimal("10.00"),
                StatusConstants.PAID
        );

        verify(paymentRepository).sumPaidPurchasePrepaymentAmountExcludingId(9L, 5L);
    }

    @Test
    void shouldRejectRefundAuditWhenPurchaseOrderPrepaymentIsNotFullyPaid() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentPurchasePrepaymentService service = service(
                mock(PurchaseOrderRepository.class),
                mock(PurchaseOrderItemRepository.class),
                paymentRepository
        );
        when(paymentRepository.sumPaidPurchasePrepaymentAmountExcludingId(9L, null))
                .thenReturn(new BigDecimal("149.99"));

        assertThatThrownBy(() -> service.assertSourcePurchaseOrderFullyPaid(sourceOrder()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款")
                .hasMessageContaining("未足额支付");
    }

    @Test
    void shouldAllowRefundAuditWhenPurchaseOrderPrepaymentExactlyCoversOriginalAmount() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentPurchasePrepaymentService service = service(
                mock(PurchaseOrderRepository.class),
                mock(PurchaseOrderItemRepository.class),
                paymentRepository
        );
        when(paymentRepository.sumPaidPurchasePrepaymentAmountExcludingId(9L, null))
                .thenReturn(new BigDecimal("150.00"));

        service.assertSourcePurchaseOrderFullyPaid(sourceOrder());

        verify(paymentRepository).sumPaidPurchasePrepaymentAmountExcludingId(9L, null);
    }

    @Test
    void shouldRejectPurchaseOrderMutationWhenAnyActivePrepaymentExists() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PaymentPurchasePrepaymentService service = new PaymentPurchasePrepaymentService(
                mock(PurchaseOrderRepository.class),
                itemRepository,
                paymentRepository,
                lockService,
                mock(ResourceRecordAccessGuard.class)
        );
        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(102L, 101L));
        when(paymentRepository.existsByPaymentPurposeAndSourcePurchaseOrderIdAndDeletedFlagFalse(
                PaymentPurposes.PURCHASE_PREPAYMENT,
                9L
        )).thenReturn(true);

        assertThatThrownBy(() -> service.assertNoActivePrepayment(9L, "修改"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款")
                .hasMessageContaining("不能修改");

        InOrder flow = inOrder(itemRepository, lockService, paymentRepository);
        flow.verify(itemRepository).findActiveIdsByPurchaseOrderId(9L);
        flow.verify(lockService).lockTradeItemSources(List.of(101L, 102L), List.of(), List.of());
        flow.verify(paymentRepository).existsByPaymentPurposeAndSourcePurchaseOrderIdAndDeletedFlagFalse(
                PaymentPurposes.PURCHASE_PREPAYMENT,
                9L
        );
    }

    @Test
    void shouldRejectUnauditingPrepaymentWhenActivePurchaseRefundExists() {
        PurchaseRefundRepository refundRepository = mock(PurchaseRefundRepository.class);
        SupplierRefundReceiptRepository refundReceiptRepository = mock(SupplierRefundReceiptRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PaymentPurchasePrepaymentService service = new PaymentPurchasePrepaymentService(
                mock(PurchaseOrderRepository.class),
                itemRepository,
                mock(PaymentRepository.class),
                lockService,
                mock(ResourceRecordAccessGuard.class),
                refundRepository,
                refundReceiptRepository
        );
        Payment payment = payment(5L);
        payment.setPaymentPurpose(PaymentPurposes.PURCHASE_PREPAYMENT);
        payment.setSourcePurchaseOrderId(9L);
        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(102L, 101L));
        when(refundRepository.existsBySourcePurchaseOrderIdAndDeletedFlagFalse(9L)).thenReturn(true);

        assertThatThrownBy(() -> service.assertRefundLifecycleMutable(payment, "反审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购退款单")
                .hasMessageContaining("不能反审核");

        InOrder flow = inOrder(itemRepository, lockService, refundRepository);
        flow.verify(itemRepository).findActiveIdsByPurchaseOrderId(9L);
        flow.verify(lockService).lockTradeItemSources(List.of(101L, 102L), List.of(), List.of());
        flow.verify(refundRepository).existsBySourcePurchaseOrderIdAndDeletedFlagFalse(9L);
        verify(refundReceiptRepository, never()).countActiveBySourcePurchaseOrderId(9L);
    }

    @Test
    void shouldRejectDeletingPrepaymentWhenSupplierRefundReceiptExists() {
        PurchaseRefundRepository refundRepository = mock(PurchaseRefundRepository.class);
        SupplierRefundReceiptRepository refundReceiptRepository = mock(SupplierRefundReceiptRepository.class);
        PaymentPurchasePrepaymentService service = service(
                mock(PurchaseOrderRepository.class),
                mock(PurchaseOrderItemRepository.class),
                mock(PaymentRepository.class),
                refundRepository,
                refundReceiptRepository
        );
        Payment payment = payment(5L);
        payment.setPaymentPurpose(PaymentPurposes.PURCHASE_PREPAYMENT);
        payment.setSourcePurchaseOrderId(9L);
        when(refundRepository.existsBySourcePurchaseOrderIdAndDeletedFlagFalse(9L)).thenReturn(false);
        when(refundReceiptRepository.countActiveBySourcePurchaseOrderId(9L)).thenReturn(1L);

        assertThatThrownBy(() -> service.assertRefundLifecycleMutable(payment, "删除"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商退款到账单")
                .hasMessageContaining("不能删除");
    }

    @Test
    void shouldRejectMissingSourcePurchaseOrder() {
        PaymentPurchasePrepaymentService service = service(
                mock(PurchaseOrderRepository.class),
                mock(PurchaseOrderItemRepository.class),
                mock(PaymentRepository.class)
        );

        assertThatThrownBy(() -> service.applySourceSnapshot(
                payment(5L),
                null,
                new BigDecimal("10.00"),
                StatusConstants.DRAFT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购订单不能为空");
    }

    @Test
    void shouldRejectSourcePurchaseOrderNotFoundAfterLock() {
        PurchaseOrderRepository orderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        PaymentPurchasePrepaymentService service = service(
                orderRepository,
                itemRepository,
                mock(PaymentRepository.class)
        );
        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(101L));
        when(orderRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applySourceSnapshot(
                payment(5L),
                9L,
                new BigDecimal("10.00"),
                StatusConstants.DRAFT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购订单不存在");
    }

    @Test
    void shouldRejectIncompleteSupplierIdentity() {
        PurchaseOrderRepository orderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        PaymentPurchasePrepaymentService service = service(
                orderRepository,
                itemRepository,
                mock(PaymentRepository.class)
        );
        PurchaseOrder sourceOrder = sourceOrder();
        sourceOrder.setSupplierCode(" ");
        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(101L, 102L));
        when(orderRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(sourceOrder));

        assertThatThrownBy(() -> service.applySourceSnapshot(
                payment(5L),
                9L,
                new BigDecimal("10.00"),
                StatusConstants.DRAFT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商编码");
    }

    @Test
    void shouldRejectIncompleteSettlementCompanySnapshot() {
        PurchaseOrderRepository orderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository itemRepository = mock(PurchaseOrderItemRepository.class);
        PaymentPurchasePrepaymentService service = service(
                orderRepository,
                itemRepository,
                mock(PaymentRepository.class)
        );
        PurchaseOrder sourceOrder = sourceOrder();
        sourceOrder.setSettlementCompanyName(null);
        when(itemRepository.findActiveIdsByPurchaseOrderId(9L)).thenReturn(List.of(101L, 102L));
        when(orderRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(sourceOrder));

        assertThatThrownBy(() -> service.applySourceSnapshot(
                payment(5L),
                9L,
                new BigDecimal("10.00"),
                StatusConstants.DRAFT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体名称");
    }

    @Test
    void shouldRejectExistingStatementAllocationsBeforePaidTransition() {
        PaymentPurchasePrepaymentService service = service(
                mock(PurchaseOrderRepository.class),
                mock(PurchaseOrderItemRepository.class),
                mock(PaymentRepository.class)
        );
        Payment payment = payment(5L);
        payment.getItems().add(new PaymentAllocation());

        assertThatThrownBy(() -> service.applySourceSnapshot(
                payment,
                9L,
                new BigDecimal("10.00"),
                StatusConstants.PAID
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款不能包含对账单核销明细");
    }

    private PaymentPurchasePrepaymentService service(PurchaseOrderRepository orderRepository,
                                                       PurchaseOrderItemRepository itemRepository,
                                                       PaymentRepository paymentRepository) {
        return new PaymentPurchasePrepaymentService(
                orderRepository,
                itemRepository,
                paymentRepository,
                mock(SourceAllocationLockService.class),
                mock(ResourceRecordAccessGuard.class)
        );
    }

    private PaymentPurchasePrepaymentService service(
            PurchaseOrderRepository orderRepository,
            PurchaseOrderItemRepository itemRepository,
            PaymentRepository paymentRepository,
            PurchaseRefundRepository refundRepository,
            SupplierRefundReceiptRepository refundReceiptRepository
    ) {
        return new PaymentPurchasePrepaymentService(
                orderRepository,
                itemRepository,
                paymentRepository,
                mock(SourceAllocationLockService.class),
                mock(ResourceRecordAccessGuard.class),
                refundRepository,
                refundReceiptRepository
        );
    }

    private Payment payment(Long id) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setItems(new ArrayList<>());
        return payment;
    }

    private PurchaseOrder sourceOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(9L);
        order.setOrderNo("PO-009");
        order.setSupplierCode("SUP-009");
        order.setSupplierName("来源供应商");
        order.setSettlementCompanyId(19L);
        order.setSettlementCompanyName("结算主体A");
        order.setStatus(StatusConstants.AUDITED);
        order.setTotalAmount(new BigDecimal("1.00"));
        order.setItems(new ArrayList<>(List.of(
                item(order, 101L, 10, "0.50000000", "20.00"),
                item(order, 102L, 2, "0.25000000", "100.00")
        )));
        return order;
    }

    private PurchaseOrderItem item(PurchaseOrder order,
                                   Long id,
                                   int quantity,
                                   String pieceWeightTon,
                                   String unitPrice) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setPurchaseOrder(order);
        item.setQuantity(quantity);
        item.setPieceWeightTon(new BigDecimal(pieceWeightTon));
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setAmount(new BigDecimal("0.01"));
        return item;
    }
}
