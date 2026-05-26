package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class SalesOrderCompletionSyncService {

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

    private boolean isFullyOutbounded(SalesOrder order, List<SalesOutbound> allOutbounds, String normalizedOrderNo) {
        for (var item : order.getItems()) {
            int totalOutbounded = allOutbounds.stream()
                    .filter(ob -> StatusConstants.AUDITED.equals(normalize(ob.getStatus())))
                    .filter(ob -> parseSalesOrderNos(ob.getSalesOrderNo()).contains(normalizedOrderNo))
                    .flatMap(ob -> ob.getItems().stream())
                    .filter(obi -> item.getId().equals(obi.getSourceSalesOrderItemId()))
                    .mapToInt(obi -> obi.getQuantity() != null ? obi.getQuantity() : 0)
                    .sum();
            if (totalOutbounded != (item.getQuantity() != null ? item.getQuantity() : 0)) {
                return false;
            }
        }
        return true;
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
