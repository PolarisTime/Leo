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

/**
 * 销售出库累计覆盖校验（支持一张订单拆分为多张出库）：
 * <ul>
 *   <li>明细非空，每行来源销售订单明细必填且数量大于等于 1；</li>
 *   <li>一张出库只允许来源同一张已审核销售订单，且不允许重复来源明细；</li>
 *   <li>每行来源明细必须属于该订单；</li>
 *   <li>跨出库累计：其它未删除出库对同一来源明细的已用数量 + 本次数量不得超过订单明细数量；</li>
 *   <li>允许只覆盖订单的部分明细，全部出满由完成同步负责推进订单状态。</li>
 * </ul>
 */
@Service
public class SalesOutboundCoverageValidator {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOutboundSourceService sourceService;

    public SalesOutboundCoverageValidator(SalesOrderRepository salesOrderRepository,
                                           SalesOutboundSourceService sourceService) {
        this.salesOrderRepository = salesOrderRepository;
        this.sourceService = sourceService;
    }

    public void assertCumulativeCoverage(SalesOutbound outbound) {
        List<SalesOutboundItem> outboundItems = outbound == null || outbound.getItems() == null
                ? List.of()
                : outbound.getItems();
        if (outboundItems.isEmpty()) {
            throw business("销售出库明细不能为空");
        }

        Map<Long, Integer> requestedQuantityBySourceItem = new LinkedHashMap<>();
        Map<Long, Integer> firstLineBySourceItem = new LinkedHashMap<>();
        for (int i = 0; i < outboundItems.size(); i++) {
            SalesOutboundItem item = outboundItems.get(i);
            int lineNo = i + 1;
            Long sourceSalesOrderItemId = item.getSourceSalesOrderItemId();
            if (sourceSalesOrderItemId == null) {
                throw business("第" + lineNo + "行来源销售订单明细不能为空");
            }
            int quantity = quantity(item.getQuantity());
            if (quantity < 1) {
                throw business("第" + lineNo + "行销售出库数量必须大于等于 1");
            }
            if (requestedQuantityBySourceItem.containsKey(sourceSalesOrderItemId)) {
                throw business("第" + lineNo + "行不能重复导入同一销售订单明细");
            }
            requestedQuantityBySourceItem.put(sourceSalesOrderItemId, quantity);
            firstLineBySourceItem.put(sourceSalesOrderItemId, lineNo);
        }

        Map<Long, SalesOrderItem> sourceItems = sourceService.loadSourceSalesOrderItemMap(outboundItems);
        if (!sourceItems.keySet().containsAll(requestedQuantityBySourceItem.keySet())) {
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
            throw business("来源销售订单状态已变化，仅已审核订单允许销售出库");
        }

        for (Map.Entry<Long, SalesOrderItem> entry : sourceItems.entrySet()) {
            SalesOrderItem sourceItem = entry.getValue();
            if (sourceItem == null || sourceItem.getSalesOrder() == null
                    || !orderId.equals(sourceItem.getSalesOrder().getId())) {
                throw business("第" + firstLineBySourceItem.get(entry.getKey()) + "行来源销售订单明细不属于来源销售订单");
            }
        }

        assertCumulativeQuantityAvailable(
                outbound == null ? null : outbound.getId(),
                requestedQuantityBySourceItem,
                firstLineBySourceItem,
                sourceItems
        );
    }

    private void assertCumulativeQuantityAvailable(Long currentOutboundId,
                                                   Map<Long, Integer> requestedQuantityBySourceItem,
                                                   Map<Long, Integer> firstLineBySourceItem,
                                                   Map<Long, SalesOrderItem> sourceItems) {
        Map<Long, Integer> alreadyOutbound =
                sourceService.sumOtherOutboundQuantitiesBySourceSalesOrderItemIds(
                        requestedQuantityBySourceItem.keySet(),
                        currentOutboundId
                );
        for (Map.Entry<Long, Integer> entry : requestedQuantityBySourceItem.entrySet()) {
            Long sourceSalesOrderItemId = entry.getKey();
            SalesOrderItem sourceItem = sourceItems.get(sourceSalesOrderItemId);
            int sourceQuantity = quantity(sourceItem == null ? null : sourceItem.getQuantity());
            int used = alreadyOutbound.getOrDefault(sourceSalesOrderItemId, 0);
            int remaining = sourceQuantity - used;
            if (entry.getValue() > remaining) {
                throw business("第" + firstLineBySourceItem.get(sourceSalesOrderItemId)
                        + "行来源销售订单明细可出库数量不足，剩余可用 " + Math.max(remaining, 0) + " 件");
            }
        }
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
