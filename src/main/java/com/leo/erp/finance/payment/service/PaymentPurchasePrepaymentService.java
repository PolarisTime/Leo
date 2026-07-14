package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PaymentPurchasePrepaymentService {

    private static final String PURCHASE_ORDER_MODULE_KEY = "purchase-order";
    private static final Set<String> ALLOWED_SOURCE_STATUSES = Set.of(
            StatusConstants.AUDITED,
            StatusConstants.PURCHASE_COMPLETED
    );

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final PaymentRepository paymentRepository;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;

    public PaymentPurchasePrepaymentService(PurchaseOrderRepository purchaseOrderRepository,
                                            PurchaseOrderItemRepository purchaseOrderItemRepository,
                                            PaymentRepository paymentRepository,
                                            SourceAllocationLockService sourceAllocationLockService,
                                            ResourceRecordAccessGuard resourceRecordAccessGuard) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.paymentRepository = paymentRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
    }

    void applySourceSnapshot(Payment payment,
                             Long sourcePurchaseOrderId,
                             BigDecimal paymentAmount,
                             String nextStatus) {
        validateNoStatementAllocations(payment);
        PurchaseOrder sourceOrder = lockAndRequireSourceOrder(payment, sourcePurchaseOrderId);
        SourceSnapshot snapshot = sourceSnapshot(sourceOrder);
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            assertPaidCapacity(payment, sourceOrder.getId(), snapshot.originalAmount(), paymentAmount);
        }
        applySnapshot(payment, sourceOrder, snapshot);
    }

    private PurchaseOrder lockAndRequireSourceOrder(Payment payment, Long targetOrderId) {
        if (targetOrderId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "来源采购订单不能为空");
        }
        TreeSet<Long> affectedOrderIds = new TreeSet<>();
        if (PaymentPurposes.isPurchasePrepayment(payment.getPaymentPurpose())
                && payment.getSourcePurchaseOrderId() != null) {
            affectedOrderIds.add(payment.getSourcePurchaseOrderId());
        }
        affectedOrderIds.add(targetOrderId);
        TreeSet<Long> sourceItemIds = new TreeSet<>();
        for (Long orderId : affectedOrderIds) {
            sourceItemIds.addAll(purchaseOrderItemRepository.findActiveIdsByPurchaseOrderId(orderId));
        }
        sourceAllocationLockService.lockTradeItemSources(
                List.copyOf(sourceItemIds),
                List.of(),
                List.of()
        );
        PurchaseOrder sourceOrder = purchaseOrderRepository.findByIdAndDeletedFlagFalse(targetOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "来源采购订单不存在"));
        resourceRecordAccessGuard.assertCurrentUserCanAccess(
                PURCHASE_ORDER_MODULE_KEY,
                "read",
                sourceOrder
        );
        if (!ALLOWED_SOURCE_STATUSES.contains(sourceOrder.getStatus())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购预付款来源采购订单状态必须为已审核或完成采购"
            );
        }
        return sourceOrder;
    }

    private SourceSnapshot sourceSnapshot(PurchaseOrder sourceOrder) {
        String orderNo = requireText(sourceOrder.getOrderNo(), "来源采购订单号不能为空");
        Long supplierId = sourceOrder.getSupplierId();
        if (supplierId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购订单供应商ID不能为空");
        }
        String supplierCode = requireText(sourceOrder.getSupplierCode(), "来源采购订单供应商编码不能为空");
        String supplierName = requireText(sourceOrder.getSupplierName(), "来源采购订单供应商名称不能为空");
        Long settlementCompanyId = sourceOrder.getSettlementCompanyId();
        if (settlementCompanyId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购订单结算主体不能为空");
        }
        String settlementCompanyName = requireText(
                sourceOrder.getSettlementCompanyName(),
                "来源采购订单结算主体名称不能为空"
        );
        BigDecimal originalAmount = originalAmount(sourceOrder);
        return new SourceSnapshot(
                orderNo,
                supplierId,
                supplierCode,
                supplierName,
                settlementCompanyId,
                settlementCompanyName,
                originalAmount
        );
    }

    private BigDecimal originalAmount(PurchaseOrder sourceOrder) {
        if (sourceOrder.getItems() == null) {
            return TradeItemCalculator.scaleAmount(BigDecimal.ZERO);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItem item : sourceOrder.getItems()) {
            BigDecimal theoreticalWeight = TradeItemCalculator.calculateWeightTon(
                    item.getQuantity(),
                    item.getPieceWeightTon()
            );
            total = total.add(TradeItemCalculator.calculateAmount(theoreticalWeight, item.getUnitPrice()));
        }
        return TradeItemCalculator.scaleAmount(total);
    }

    private void assertPaidCapacity(Payment payment,
                                    Long sourcePurchaseOrderId,
                                    BigDecimal originalAmount,
                                    BigDecimal paymentAmount) {
        BigDecimal alreadyPaid = TradeItemCalculator.safeBigDecimal(
                paymentRepository.sumPaidPurchasePrepaymentAmountExcludingId(
                        sourcePurchaseOrderId,
                        payment.getId()
                )
        );
        BigDecimal nextPaid = TradeItemCalculator.scaleAmount(
                alreadyPaid.add(TradeItemCalculator.safeBigDecimal(paymentAmount))
        );
        if (nextPaid.compareTo(originalAmount) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购预付款累计金额不能超过采购订单原始金额");
        }
    }

    private void applySnapshot(Payment payment,
                               PurchaseOrder sourceOrder,
                               SourceSnapshot snapshot) {
        if (payment.getCounterpartyId() != null
                && !payment.getCounterpartyId().equals(snapshot.supplierId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "付款单往来方ID与来源采购订单供应商ID不一致");
        }
        payment.setSourcePurchaseOrderId(sourceOrder.getId());
        payment.setPurchaseOrderNo(snapshot.purchaseOrderNo());
        payment.setSupplierCode(snapshot.supplierCode());
        payment.setSupplierName(snapshot.supplierName());
        payment.setSettlementCompanyId(snapshot.settlementCompanyId());
        payment.setSettlementCompanyName(snapshot.settlementCompanyName());
        payment.setBusinessType(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE);
        payment.setCounterpartyType(PaymentAllocationService.SUPPLIER_PAYMENT_TYPE);
        payment.setCounterpartyId(snapshot.supplierId());
        payment.setCounterpartyCode(snapshot.supplierCode());
        payment.setCounterpartyName(snapshot.supplierName());
    }

    void validateNoStatementAllocations(Payment payment) {
        if (payment.getItems() != null && !payment.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "采购预付款不能包含对账单核销明细");
        }
    }

    public void assertNoActivePrepayment(Long sourcePurchaseOrderId, String operationName) {
        if (sourcePurchaseOrderId == null) {
            return;
        }
        lockSourcePurchaseOrderItems(sourcePurchaseOrderId);
        if (paymentRepository.existsByPaymentPurposeAndSourcePurchaseOrderIdAndDeletedFlagFalse(
                PaymentPurposes.PURCHASE_PREPAYMENT,
                sourcePurchaseOrderId
        )) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单已存在采购预付款，不能" + operationName
            );
        }
    }

    private void lockSourcePurchaseOrderItems(Long sourcePurchaseOrderId) {
        TreeSet<Long> sourceItemIds = new TreeSet<>(
                purchaseOrderItemRepository.findActiveIdsByPurchaseOrderId(sourcePurchaseOrderId)
        );
        sourceAllocationLockService.lockTradeItemSources(
                List.copyOf(sourceItemIds),
                List.of(),
                List.of()
        );
    }

    private String requireText(String value, String message) {
        String normalized = BusinessDocumentValidator.trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
        return normalized;
    }

    private record SourceSnapshot(
            String purchaseOrderNo,
            Long supplierId,
            String supplierCode,
            String supplierName,
            Long settlementCompanyId,
            String settlementCompanyName,
            BigDecimal originalAmount
    ) {
    }
}
