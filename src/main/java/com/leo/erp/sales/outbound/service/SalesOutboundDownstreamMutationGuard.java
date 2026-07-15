package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderDeliveryVerificationGuard;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;

@Service
public class SalesOutboundDownstreamMutationGuard {

    private final SalesOrderDeliveryVerificationGuard deliveryVerificationGuard;
    private final SalesOrderRepository salesOrderRepository;

    public SalesOutboundDownstreamMutationGuard(SalesOrderDeliveryVerificationGuard deliveryVerificationGuard,
                                                SalesOrderRepository salesOrderRepository) {
        this.deliveryVerificationGuard = deliveryVerificationGuard;
        this.salesOrderRepository = salesOrderRepository;
    }

    public void assertReverseAuditAllowed(SalesOutbound outbound) {
        List<Long> sourceSalesOrderItemIds = sourceSalesOrderItemIds(outbound);
        if (!sourceSalesOrderItemIds.isEmpty()) {
            assertSourceOrderNotCompleted(sourceSalesOrderItemIds, "反审核");
            deliveryVerificationGuard.assertMutableByItemIds(sourceSalesOrderItemIds, "反审核");
        }
    }

    public void assertDeleteAllowed(SalesOutbound outbound) {
        List<Long> sourceSalesOrderItemIds = sourceSalesOrderItemIds(outbound);
        if (!sourceSalesOrderItemIds.isEmpty()) {
            assertSourceOrderNotCompleted(sourceSalesOrderItemIds, "删除");
        }
    }

    private void assertSourceOrderNotCompleted(List<Long> sourceSalesOrderItemIds, String action) {
        boolean completed = salesOrderRepository.findAllWithItemsBySourceItemIds(sourceSalesOrderItemIds).stream()
                .anyMatch(order -> StatusConstants.SALES_COMPLETED.equals(normalize(order.getStatus())));
        if (completed) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "来源销售订单已完成销售，不能" + action + "销售出库"
            );
        }
    }

    private List<Long> sourceSalesOrderItemIds(SalesOutbound outbound) {
        if (outbound == null || outbound.getItems() == null || outbound.getItems().isEmpty()) {
            return List.of();
        }
        TreeSet<Long> sourceIds = new TreeSet<>();
        outbound.getItems().stream()
                .map(SalesOutboundItem::getSourceSalesOrderItemId)
                .filter(java.util.Objects::nonNull)
                .forEach(sourceIds::add);
        return List.copyOf(sourceIds);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
