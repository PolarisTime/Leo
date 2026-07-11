package com.leo.erp.purchase.refund.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PurchaseRefundInvoiceCapacityGuard {

    private final InvoiceReceiptRepository invoiceReceiptRepository;

    public PurchaseRefundInvoiceCapacityGuard(InvoiceReceiptRepository invoiceReceiptRepository) {
        this.invoiceReceiptRepository = invoiceReceiptRepository;
    }

    public void assertRefundFits(PurchaseOrder sourceOrder,
                                 PurchaseRefundCalculator.Calculation calculation) {
        List<Long> sourceItemIds = sourceOrder.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (sourceItemIds.isEmpty() || calculation.lines().isEmpty()) {
            return;
        }
        Map<Long, InvoiceAllocation> invoiceAllocations = loadInvoiceAllocations(sourceItemIds);
        for (PurchaseRefundCalculator.Line line : calculation.lines()) {
            PurchaseOrderItem sourceItem = line.sourceItem();
            Long sourceItemId = sourceItem.getId();
            if (sourceItemId == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购订单明细ID不能为空");
            }
            InvoiceAllocation invoice = invoiceAllocations.getOrDefault(
                    sourceItemId,
                    InvoiceAllocation.EMPTY
            );
            long sourceQuantity = sourceItem.getQuantity() == null ? 0L : sourceItem.getQuantity().longValue();
            if (Math.addExact(invoice.quantity(), line.quantity()) > sourceQuantity) {
                throw conflict("数量");
            }
            BigDecimal sourceWeight = TradeItemCalculator.calculateWeightTon(
                    sourceItem.getQuantity(),
                    sourceItem.getPieceWeightTon()
            );
            if (invoice.weightTon().add(line.weightTon()).compareTo(sourceWeight) > 0) {
                throw conflict("吨位");
            }
            BigDecimal sourceAmount = TradeItemCalculator.calculateAmount(
                    sourceWeight,
                    sourceItem.getUnitPrice()
            );
            if (invoice.amount().add(line.amount()).compareTo(sourceAmount) > 0) {
                throw conflict("金额");
            }
        }
    }

    private Map<Long, InvoiceAllocation> loadInvoiceAllocations(List<Long> sourceItemIds) {
        Map<Long, InvoiceAllocation> result = new HashMap<>();
        for (InvoiceReceiptRepository.SourceAllocationSummary summary
                : invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(sourceItemIds, null)) {
            result.put(
                    summary.getSourcePurchaseOrderItemId(),
                    new InvoiceAllocation(
                            summary.getTotalQuantity() == null ? 0L : summary.getTotalQuantity(),
                            TradeItemCalculator.safeBigDecimal(summary.getTotalWeightTon()),
                            TradeItemCalculator.safeBigDecimal(summary.getTotalAmount())
                    )
            );
        }
        return result;
    }

    private BusinessException conflict(String dimension) {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "采购订单已收票" + dimension + "与退款" + dimension + "合计超过采购订单，请先调整收票单"
        );
    }

    private record InvoiceAllocation(long quantity, BigDecimal weightTon, BigDecimal amount) {

        private static final InvoiceAllocation EMPTY = new InvoiceAllocation(
                0L,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
