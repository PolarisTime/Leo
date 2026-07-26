package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.purchase.api.PurchaseOrderPrepaymentReferenceGuard;
import com.leo.erp.purchase.api.PurchaseOrderPrepaymentSnapshot;
import com.leo.erp.purchase.api.PurchaseOrderPrepaymentSourceQuery;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

@Service
public class PaymentPurchasePrepaymentService implements PurchaseOrderPrepaymentReferenceGuard {

    private final PurchaseOrderPrepaymentSourceQuery purchaseOrderPrepaymentSourceQuery;
    private final PaymentRepository paymentRepository;
    private final SourceAllocationLockService sourceAllocationLockService;

    public PaymentPurchasePrepaymentService(PurchaseOrderPrepaymentSourceQuery purchaseOrderPrepaymentSourceQuery,
                                            PaymentRepository paymentRepository,
                                            SourceAllocationLockService sourceAllocationLockService) {
        this.purchaseOrderPrepaymentSourceQuery = purchaseOrderPrepaymentSourceQuery;
        this.paymentRepository = paymentRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    void applySourceSnapshot(Payment payment,
                             Long sourcePurchaseOrderId,
                             BigDecimal paymentAmount,
                             String nextStatus) {
        validateNoStatementAllocations(payment);
        PurchaseOrderPrepaymentSnapshot snapshot = purchaseOrderPrepaymentSourceQuery.lockAndRequire(
                sourcePurchaseOrderId,
                affectedPurchaseOrderIds(payment, sourcePurchaseOrderId)
        );
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            assertPaidCapacity(payment, snapshot.purchaseOrderId(), snapshot.originalAmount(), paymentAmount);
        }
        applySnapshot(payment, snapshot);
    }

    private Collection<Long> affectedPurchaseOrderIds(Payment payment, Long targetOrderId) {
        TreeSet<Long> affectedOrderIds = new TreeSet<>();
        if (PaymentPurposes.isPurchasePrepayment(payment.getPaymentPurpose())
                && payment.getSourcePurchaseOrderId() != null) {
            affectedOrderIds.add(payment.getSourcePurchaseOrderId());
        }
        if (targetOrderId != null) {
            affectedOrderIds.add(targetOrderId);
        }
        return List.copyOf(affectedOrderIds);
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

    private void applySnapshot(Payment payment, PurchaseOrderPrepaymentSnapshot snapshot) {
        if (payment.getCounterpartyId() != null
                && !payment.getCounterpartyId().equals(snapshot.supplierId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "付款单往来方ID与来源采购订单供应商ID不一致");
        }
        payment.setSourcePurchaseOrderId(snapshot.purchaseOrderId());
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

    @Override
    public void assertNoActivePrepayment(Long sourcePurchaseOrderId,
                                         Collection<Long> sourcePurchaseOrderItemIds,
                                         String operationName) {
        if (sourcePurchaseOrderId == null) {
            return;
        }
        lockSourcePurchaseOrderItems(sourcePurchaseOrderItemIds);
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

    private void lockSourcePurchaseOrderItems(Collection<Long> sourcePurchaseOrderItemIds) {
        TreeSet<Long> sourceItemIds = new TreeSet<>();
        if (sourcePurchaseOrderItemIds != null) {
            sourcePurchaseOrderItemIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .forEach(sourceItemIds::add);
        }
        sourceAllocationLockService.lockTradeItemSources(
                List.copyOf(sourceItemIds),
                List.of(),
                List.of()
        );
    }
}
