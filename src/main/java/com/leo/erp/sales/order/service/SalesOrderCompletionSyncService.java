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

        Set<String> completedOrderNos = new LinkedHashSet<>();
        for (SalesOutbound outbound : salesOutboundRepository.findByDeletedFlagFalse()) {
            if (!StatusConstants.AUDITED.equals(normalize(outbound.getStatus()))) {
                continue;
            }
            for (String orderNo : parseSalesOrderNos(outbound.getSalesOrderNo())) {
                if (orderNos.contains(orderNo)) {
                    completedOrderNos.add(orderNo);
                }
            }
        }

        List<SalesOrder> changedOrders = orders.stream()
                .filter(order -> applyCompletedStatus(order, completedOrderNos.contains(normalize(order.getOrderNo()))))
                .toList();
        if (!changedOrders.isEmpty()) {
            salesOrderRepository.saveAll(changedOrders);
        }
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
