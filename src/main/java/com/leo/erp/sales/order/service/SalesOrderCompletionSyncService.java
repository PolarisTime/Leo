package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
    private final SalesOrderOutboundQueryService outboundQueryService;

    public SalesOrderCompletionSyncService(SalesOrderRepository salesOrderRepository,
                                           SalesOrderOutboundQueryService outboundQueryService) {
        this.salesOrderRepository = salesOrderRepository;
        this.outboundQueryService = outboundQueryService;
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

        // 收集所有未删除销售出库，用于销售订单完成状态判断。
        List<SalesOrderOutboundQueryService.OutboundRecord> allOutbounds = outboundQueryService.findActiveOutbounds();
        List<SalesOrder> changedOrders = new ArrayList<>();
        for (SalesOrder order : orders) {
            String normalizedOrderNo = normalize(order.getOrderNo());
            boolean fullyOutbounded = isFullyOutbounded(order, allOutbounds, normalizedOrderNo);
            if (applyCompletedStatus(order, fullyOutbounded && isPriced(order))) {
                changedOrders.add(order);
            }
        }
        if (!changedOrders.isEmpty()) {
            salesOrderRepository.saveAll(changedOrders);
        }
    }

    private boolean isFullyOutbounded(
            SalesOrder order,
            List<SalesOrderOutboundQueryService.OutboundRecord> allOutbounds,
            String normalizedOrderNo
    ) {
        // Pre-compute: aggregate outbound quantities by sales order item ID
        Map<Long, Integer> outboundQtyByItemId = allOutbounds.stream()
                .filter(ob -> StatusConstants.AUDITED.equals(normalize(ob.status())))
                .filter(ob -> parseSalesOrderNos(ob.salesOrderNo())
                        .contains(normalizedOrderNo))
                .flatMap(ob -> ob.items().stream())
                .filter(obi -> obi.sourceSalesOrderItemId() != null)
                .collect(Collectors.groupingBy(
                        SalesOrderOutboundQueryService.OutboundItemRecord::sourceSalesOrderItemId,
                        Collectors.summingInt(
                                obi -> obi.quantity() != null ? obi.quantity() : 0
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
                && !StatusConstants.DELIVERY_VERIFICATION.equals(currentStatus)
                && !StatusConstants.SALES_COMPLETED.equals(currentStatus)) {
            return false;
        }
        if (completed) {
            if (!StatusConstants.AUDITED.equals(currentStatus)) {
                return false;
            }
            order.setStatus(StatusConstants.DELIVERY_VERIFICATION);
            return true;
        }
        if (StatusConstants.AUDITED.equals(currentStatus)) {
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
