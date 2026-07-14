package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SalesOrderCompletionSyncService {

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
            if (applyDeliveryStatus(order, fullyOutbounded)) {
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

            return actual == expected;
        });
    }

    private boolean applyDeliveryStatus(SalesOrder order, boolean delivered) {
        String currentStatus = normalize(order.getStatus());
        if (!StatusConstants.AUDITED.equals(currentStatus)
                && !StatusConstants.DELIVERY_VERIFICATION.equals(currentStatus)) {
            return false;
        }
        if (delivered) {
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
