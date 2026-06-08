package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SalesOrderCompletionSyncService {

    /**
     * Fulfillment tolerance: allow up to 5% under-fulfillment.
     * Over-fulfillment is not tolerated (upper bound = exact match).
     * E.g., if expected=100, actual can be 95–100.
     */
    private static final BigDecimal FULFILLMENT_TOLERANCE = new BigDecimal("0.05");

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOutboundRepository salesOutboundRepository;

    public SalesOrderCompletionSyncService(SalesOrderRepository salesOrderRepository,
                                           SalesOutboundRepository salesOutboundRepository) {
        this.salesOrderRepository = salesOrderRepository;
        this.salesOutboundRepository = salesOutboundRepository;
    }

    @Transactional
    public void syncBySalesOrderReference(String salesOrderReference) {
        Set<String> orderNos = parseSalesOrderNos(salesOrderReference);
        if (orderNos.isEmpty()) {
            return;
        }

        List<SalesOrder> orders = salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(orderNos);
        if (orders.isEmpty()) {
            return;
        }

        // 收集所有已审核出库中引用这些SO的明细
        List<SalesOutbound> allOutbounds = salesOutboundRepository.findByDeletedFlagFalse();
        for (SalesOrder order : orders) {
            String normalizedOrderNo = normalize(order.getOrderNo());
            syncItemWeightAndAmount(order, allOutbounds, normalizedOrderNo);
            boolean fullyOutbounded = isFullyOutbounded(order, allOutbounds, normalizedOrderNo);
            applyCompletedStatus(order, fullyOutbounded && isPriced(order));
        }
        List<SalesOrder> changedOrders = orders.stream()
                .filter(order -> order.getStatus() != null)
                .toList();
        if (!changedOrders.isEmpty()) {
            salesOrderRepository.saveAll(changedOrders);
        }
    }

    private void syncItemWeightAndAmount(
            SalesOrder order,
            List<SalesOutbound> allOutbounds,
            String normalizedOrderNo
    ) {
        Map<Long, BigDecimal> cumulativeWeightByItemId = allOutbounds.stream()
                .filter(ob -> StatusConstants.AUDITED.equals(normalize(ob.getStatus())))
                .filter(ob -> parseSalesOrderNos(ob.getSalesOrderNo())
                        .contains(normalizedOrderNo))
                .flatMap(ob -> ob.getItems().stream())
                .filter(obi -> obi.getSourceSalesOrderItemId() != null && obi.getWeightTon() != null)
                .collect(Collectors.groupingBy(
                        obi -> obi.getSourceSalesOrderItemId(),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                obi -> obi.getWeightTon(),
                                BigDecimal::add
                        )
                ));

        order.getItems().forEach(item -> {
            BigDecimal cumulativeWeight = cumulativeWeightByItemId.getOrDefault(
                    item.getId(), BigDecimal.ZERO);
            if (cumulativeWeight.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            // 首次反写前保存原始计划重量
            if (item.getOriginalWeightTon() == null
                    && cumulativeWeight.compareTo(item.getWeightTon()) != 0) {
                item.setOriginalWeightTon(item.getWeightTon());
            }
            item.setWeightTon(cumulativeWeight);
            if (item.getUnitPrice() != null) {
                item.setAmount(cumulativeWeight.multiply(item.getUnitPrice())
                        .setScale(2, RoundingMode.HALF_UP));
            }
        });
        recalculateTotals(order);
    }

    private void recalculateTotals(SalesOrder order) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (SalesOrderItem item : order.getItems()) {
            if (item.getWeightTon() != null) {
                totalWeight = totalWeight.add(item.getWeightTon());
            }
            if (item.getAmount() != null) {
                totalAmount = totalAmount.add(item.getAmount());
            }
        }
        order.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        order.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
    }

    private boolean isFullyOutbounded(
            SalesOrder order,
            List<SalesOutbound> allOutbounds,
            String normalizedOrderNo
    ) {
        // Pre-compute: aggregate outbound quantities by sales order item ID
        Map<Long, Integer> outboundQtyByItemId = allOutbounds.stream()
                .filter(ob -> StatusConstants.AUDITED.equals(normalize(ob.getStatus())))
                .filter(ob -> parseSalesOrderNos(ob.getSalesOrderNo())
                        .contains(normalizedOrderNo))
                .flatMap(ob -> ob.getItems().stream())
                .filter(obi -> obi.getSourceSalesOrderItemId() != null)
                .collect(Collectors.groupingBy(
                        obi -> obi.getSourceSalesOrderItemId(),
                        Collectors.summingInt(
                                obi -> obi.getQuantity() != null ? obi.getQuantity() : 0
                        )
                ));

        // Check each order item against pre-computed map with tolerance
        return order.getItems().stream().allMatch(item -> {
            int expected = item.getQuantity() != null ? item.getQuantity() : 0;
            int actual = outboundQtyByItemId.getOrDefault(item.getId(), 0);

            // Zero-quantity items: must match exactly
            if (expected == 0) {
                return actual == 0;
            }

            // Calculate fulfillment ratio — only allow under-fulfillment tolerance (≤5%);
            // over-fulfillment is not allowed for piece-count items
            BigDecimal ratio = BigDecimal.valueOf(actual)
                    .divide(BigDecimal.valueOf(expected), 4, RoundingMode.HALF_UP);
            BigDecimal lowerBound = BigDecimal.ONE.subtract(FULFILLMENT_TOLERANCE);
            BigDecimal upperBound = BigDecimal.ONE;

            return ratio.compareTo(lowerBound) >= 0
                    && ratio.compareTo(upperBound) <= 0;
        });
    }

    private boolean applyCompletedStatus(SalesOrder order, boolean completed) {
        String currentStatus = normalize(order.getStatus());
        if (!StatusConstants.AUDITED.equals(currentStatus)
                && !StatusConstants.SALES_COMPLETED.equals(currentStatus)) {
            return false;
        }
        if (completed) {
            if (StatusConstants.SALES_COMPLETED.equals(currentStatus)) {
                return false;
            }
            order.setStatus(StatusConstants.SALES_COMPLETED);
            return true;
        }
        if (!StatusConstants.SALES_COMPLETED.equals(currentStatus)) {
            return false;
        }
        order.setStatus(StatusConstants.AUDITED);
        return true;
    }

    private boolean isPriced(SalesOrder order) {
        return order.getItems().stream()
                .allMatch(item -> item.getUnitPrice() != null
                        && item.getUnitPrice().compareTo(BigDecimal.ZERO) > 0);
    }

    private Set<String> parseSalesOrderNos(String salesOrderReference) {
        if (salesOrderReference == null || salesOrderReference.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(salesOrderReference.split(","))
                .map(this::normalize)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
