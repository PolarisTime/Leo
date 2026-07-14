package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SalesOutboundCoverageValidator {

    private static final String COVERAGE_MESSAGE = "销售出库必须一次覆盖销售订单全部明细和数量";

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOutboundSourceService sourceService;

    public SalesOutboundCoverageValidator(SalesOrderRepository salesOrderRepository,
                                           SalesOutboundSourceService sourceService) {
        this.salesOrderRepository = salesOrderRepository;
        this.sourceService = sourceService;
    }

    public void assertExactCoverage(SalesOutbound outbound) {
        List<SalesOutboundItem> outboundItems = outbound == null || outbound.getItems() == null
                ? List.of()
                : outbound.getItems();
        if (outboundItems.isEmpty()) {
            throw business(COVERAGE_MESSAGE);
        }

        Map<Long, SalesOrderItem> sourceItems = sourceService.loadSourceSalesOrderItemMap(outboundItems);
        Set<Long> sourceItemIds = outboundItems.stream()
                .map(SalesOutboundItem::getSourceSalesOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sourceItemIds.size() != outboundItems.size() || sourceItems.size() != sourceItemIds.size()) {
            throw business("销售出库来源明细不能为空、重复或已失效");
        }

        Set<Long> orderIds = sourceItems.values().stream()
                .map(SalesOrderItem::getSalesOrder)
                .filter(Objects::nonNull)
                .map(SalesOrder::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (orderIds.size() != 1) {
            throw business("一张销售出库只能来源于一张销售订单");
        }

        Long orderId = orderIds.iterator().next();
        SalesOrder order = salesOrderRepository.findByIdAndDeletedFlagFalse(orderId)
                .orElseThrow(() -> business("来源销售订单不存在或已删除"));
        if (!StatusConstants.AUDITED.equals(normalize(order.getStatus()))) {
            throw business("来源销售订单状态已变化，仅已审核订单允许销售出库审核");
        }

        Map<Long, Integer> expected = order.getItems().stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(
                        SalesOrderItem::getId,
                        item -> quantity(item.getQuantity()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, Integer> actual = outboundItems.stream()
                .collect(Collectors.toMap(
                        SalesOutboundItem::getSourceSalesOrderItemId,
                        item -> quantity(item.getQuantity()),
                        (left, right) -> {
                            throw business("销售出库不能重复导入同一销售订单明细");
                        },
                        LinkedHashMap::new
                ));
        if (!expected.equals(actual)) {
            throw business(COVERAGE_MESSAGE);
        }

        sourceService.assertSourceSalesOrderItemsNotOccupied(sourceItemIds, outbound.getId());
    }

    private int quantity(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static BusinessException business(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }
}
