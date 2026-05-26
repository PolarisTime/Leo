package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
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
     * Fulfillment tolerance: allow 5% over-fulfillment.
     * E.g., if expected=100, actual can be 95-105.
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
            // 计算该SO每项明细的已出库总量
            boolean fullyOutbounded = isFullyOutbounded(order, allOutbounds, normalizedOrderNo);
            applyCompletedStatus(order, fullyOutbounded);
        }
        List<SalesOrder> changedOrders = orders.stream()
                .filter(order -> order.getStatus() != null)
                .toList();
        if (!changedOrders.isEmpty()) {
            salesOrderRepository.saveAll(changedOrders);
        }
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

            // Calculate fulfillment ratio with tolerance
            BigDecimal ratio = BigDecimal.valueOf(actual)
                    .divide(BigDecimal.valueOf(expected), 4, RoundingMode.HALF_UP);
            BigDecimal lowerBound = BigDecimal.ONE.subtract(FULFILLMENT_TOLERANCE);
            BigDecimal upperBound = BigDecimal.ONE.add(FULFILLMENT_TOLERANCE);

            return ratio.compareTo(lowerBound) >= 0
                    && ratio.compareTo(upperBound) <= 0;
        });
    }

    private boolean applyCompletedStatus(SalesOrder order, boolean completed) {
        String currentStatus = normalize(order.getStatus());
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
