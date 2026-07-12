package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
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
    public void syncBySourceSalesOrderItemIds(Collection<Long> sourceSalesOrderItemIds) {
        Set<Long> sourceItemIds = normalizeIds(sourceSalesOrderItemIds);
        if (sourceItemIds.isEmpty()) {
            return;
        }
        synchronizeOrders(salesOrderRepository.findAllWithItemsBySourceItemIds(sourceItemIds));
    }

    @Transactional
    public void syncBySalesOrderId(Long salesOrderId) {
        if (salesOrderId == null || salesOrderId <= 0) {
            return;
        }
        salesOrderRepository.findByIdAndDeletedFlagFalse(salesOrderId)
                .ifPresent(order -> synchronizeOrders(List.of(order)));
    }

    private void synchronizeOrders(List<SalesOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Set<Long> orderItemIds = orders.stream()
                .filter(order -> order.getItems() != null)
                .flatMap(order -> order.getItems().stream())
                .map(item -> item.getId())
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (orderItemIds.isEmpty()) {
            return;
        }
        List<SalesOrderOutboundQueryService.OutboundRecord> auditedOutbounds =
                outboundQueryService.findAuditedOutboundsBySourceSalesOrderItemIds(orderItemIds);
        Map<Long, Integer> outboundQtyByItemId = aggregateOutboundQuantities(auditedOutbounds, orderItemIds);
        List<SalesOrder> changedOrders = new ArrayList<>();
        for (SalesOrder order : orders) {
            boolean fullyOutbounded = isFullyOutbounded(order, outboundQtyByItemId);
            if (applyCompletedStatus(order, fullyOutbounded && isPriced(order))) {
                changedOrders.add(order);
            }
        }
        if (!changedOrders.isEmpty()) {
            salesOrderRepository.saveAll(changedOrders);
        }
    }

    private Map<Long, Integer> aggregateOutboundQuantities(
            List<SalesOrderOutboundQueryService.OutboundRecord> auditedOutbounds,
            Set<Long> orderItemIds
    ) {
        return auditedOutbounds.stream()
                .filter(ob -> StatusConstants.AUDITED.equals(normalize(ob.status())))
                .flatMap(ob -> ob.items().stream())
                .filter(obi -> orderItemIds.contains(obi.sourceSalesOrderItemId()))
                .collect(Collectors.groupingBy(
                        SalesOrderOutboundQueryService.OutboundItemRecord::sourceSalesOrderItemId,
                        Collectors.summingInt(
                                obi -> obi.quantity() != null ? obi.quantity() : 0
                        )
                ));
    }

    private boolean isFullyOutbounded(SalesOrder order, Map<Long, Integer> outboundQtyByItemId) {
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

    private Set<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
