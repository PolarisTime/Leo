package com.leo.erp.purchase.refund.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PurchaseRefundCalculator {

    private static final Set<String> EFFECTIVE_INBOUND_STATUSES = Set.of(
            StatusConstants.AUDITED,
            StatusConstants.INBOUND_COMPLETED
    );

    public Calculation calculate(PurchaseOrder order,
                                 List<PurchaseInboundItem> inboundItems) {
        Map<Long, InboundProgress> inboundProgress = summarizeInbound(inboundItems);
        List<Line> lines = new ArrayList<>();
        int totalQuantity = 0;
        BigDecimal totalWeight = TradeItemCalculator.scaleWeightTon(BigDecimal.ZERO);
        BigDecimal totalAmount = TradeItemCalculator.scaleAmount(BigDecimal.ZERO);
        boolean fullyInbound = order.getItems() != null && !order.getItems().isEmpty();

        List<PurchaseOrderItem> sourceItems = order.getItems() == null
                ? List.of()
                : order.getItems().stream().sorted(sourceItemComparator()).toList();
        for (PurchaseOrderItem sourceItem : sourceItems) {
            InboundProgress inbound = inboundProgress.getOrDefault(sourceItem.getId(), InboundProgress.EMPTY);
            int orderedQuantity = sourceItem.getQuantity() == null ? 0 : sourceItem.getQuantity();
            int remainingQuantity = (int) Math.max(
                    (long) orderedQuantity - inbound.quantity(),
                    0L
            );
            BigDecimal theoreticalWeight = TradeItemCalculator.calculateWeightTon(
                    orderedQuantity,
                    sourceItem.getPieceWeightTon()
            );
            BigDecimal remainingWeight = nonNegativeWeight(
                    theoreticalWeight.subtract(inbound.weightTon())
            );
            BigDecimal originalPaymentBasis = TradeItemCalculator.calculateAmount(
                    theoreticalWeight,
                    sourceItem.getUnitPrice()
            );
            BigDecimal remainingAmount = nonNegativeAmount(
                    originalPaymentBasis.subtract(inbound.settlementAmount())
            );
            fullyInbound &= inbound.quantity() >= orderedQuantity;
            if (remainingQuantity <= 0
                    && remainingWeight.compareTo(BigDecimal.ZERO) <= 0
                    && remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Line line = new Line(sourceItem, remainingQuantity, remainingWeight, remainingAmount);
            lines.add(line);
            totalQuantity = Math.addExact(totalQuantity, remainingQuantity);
            totalWeight = TradeItemCalculator.scaleWeightTon(totalWeight.add(remainingWeight));
            totalAmount = TradeItemCalculator.scaleAmount(totalAmount.add(remainingAmount));
        }
        return new Calculation(List.copyOf(lines), totalQuantity, totalWeight, totalAmount, fullyInbound);
    }

    private Map<Long, InboundProgress> summarizeInbound(List<PurchaseInboundItem> items) {
        Map<Long, InboundProgress> progress = new HashMap<>();
        if (items == null) {
            return progress;
        }
        for (PurchaseInboundItem item : items) {
            PurchaseInbound inbound = item.getPurchaseInbound();
            if (item.getSourcePurchaseOrderItemId() == null
                    || inbound == null
                    || inbound.isDeletedFlag()
                    || !EFFECTIVE_INBOUND_STATUSES.contains(inbound.getStatus())) {
                continue;
            }
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            BigDecimal actualWeight = item.getWeighWeightTon() != null
                    ? item.getWeighWeightTon()
                    : item.getWeightTon();
            BigDecimal settlementAmount = TradeItemCalculator.scaleAmount(
                    TradeItemCalculator.safeBigDecimal(item.getAmount())
                            .add(TradeItemCalculator.safeBigDecimal(item.getWeightAdjustmentAmount()))
            );
            progress.merge(
                    item.getSourcePurchaseOrderItemId(),
                    new InboundProgress(
                            quantity,
                            TradeItemCalculator.scaleWeightTon(actualWeight),
                            settlementAmount
                    ),
                    InboundProgress::merge
            );
        }
        return progress;
    }

    private BigDecimal nonNegativeWeight(BigDecimal value) {
        return TradeItemCalculator.scaleWeightTon(value.max(BigDecimal.ZERO));
    }

    private BigDecimal nonNegativeAmount(BigDecimal value) {
        return TradeItemCalculator.scaleAmount(value.max(BigDecimal.ZERO));
    }

    private Comparator<PurchaseOrderItem> sourceItemComparator() {
        return Comparator
                .comparing(PurchaseOrderItem::getLineNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(PurchaseOrderItem::getId, Comparator.nullsLast(Long::compareTo));
    }

    private record InboundProgress(long quantity,
                                   BigDecimal weightTon,
                                   BigDecimal settlementAmount) {

        private static final InboundProgress EMPTY = new InboundProgress(
                0L,
                TradeItemCalculator.scaleWeightTon(BigDecimal.ZERO),
                TradeItemCalculator.scaleAmount(BigDecimal.ZERO)
        );

        private InboundProgress merge(InboundProgress other) {
            return new InboundProgress(
                    Math.addExact(quantity, other.quantity),
                    TradeItemCalculator.scaleWeightTon(weightTon.add(other.weightTon)),
                    TradeItemCalculator.scaleAmount(settlementAmount.add(other.settlementAmount))
            );
        }
    }

    public record Line(
            PurchaseOrderItem sourceItem,
            int quantity,
            BigDecimal weightTon,
            BigDecimal amount
    ) {
    }

    public record Calculation(
            List<Line> lines,
            int totalQuantity,
            BigDecimal totalWeight,
            BigDecimal totalAmount,
            boolean fullyInbound
    ) {

        public boolean hasPositiveRefund() {
            return totalQuantity > 0
                    || totalWeight.compareTo(BigDecimal.ZERO) > 0
                    || totalAmount.compareTo(BigDecimal.ZERO) > 0;
        }
    }
}
