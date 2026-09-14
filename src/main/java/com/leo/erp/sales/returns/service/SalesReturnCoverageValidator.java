package com.leo.erp.sales.returns.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceOrder;
import com.leo.erp.logistics.bill.repository.FreightBillSourceOrderRepository;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.sales.returns.repository.SalesReturnItemRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 销售退货覆盖校验：
 * <ul>
 *   <li>明细必须带来源销售出库明细，且来源出库已审核；</li>
 *   <li>可选来源物流单必须已审核且其来源销售订单包含该退货行；</li>
 *   <li>累计已审核退货数量（含本次）不得超过来源出库明细数量；</li>
 *   <li>一张退货单只允许来源同一销售订单。</li>
 * </ul>
 */
@Service
public class SalesReturnCoverageValidator {

    private final SalesReturnSourceService sourceService;
    private final SalesReturnItemRepository salesReturnItemRepository;
    private final FreightBillRepository freightBillRepository;
    private final FreightBillSourceOrderRepository freightBillSourceOrderRepository;

    public SalesReturnCoverageValidator(SalesReturnSourceService sourceService,
                                        SalesReturnItemRepository salesReturnItemRepository,
                                        FreightBillRepository freightBillRepository,
                                        FreightBillSourceOrderRepository freightBillSourceOrderRepository) {
        this.sourceService = sourceService;
        this.salesReturnItemRepository = salesReturnItemRepository;
        this.freightBillRepository = freightBillRepository;
        this.freightBillSourceOrderRepository = freightBillSourceOrderRepository;
    }

    public void assertCoverage(SalesReturn salesReturn) {
        List<SalesReturnItem> items = salesReturn == null || salesReturn.getItems() == null
                ? List.of()
                : salesReturn.getItems();
        if (items.isEmpty()) {
            throw business("销售退货单明细不能为空");
        }

        Set<Long> outboundItemIds = new LinkedHashSet<>();
        Map<Integer, Long> lineOutboundItemId = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            SalesReturnItem item = items.get(i);
            int lineNo = i + 1;
            Long outboundItemId = item.getSourceSalesOutboundItemId();
            if (outboundItemId == null) {
                throw business("第" + lineNo + "行来源销售出库明细不能为空");
            }
            if (!lineOutboundItemId.containsValue(outboundItemId)) {
                lineOutboundItemId.put(lineNo, outboundItemId);
            }
            outboundItemIds.add(outboundItemId);
        }

        Map<Long, SalesOutboundItem> outboundMap = sourceService.loadSourceOutboundItemMap(outboundItemIds);
        if (outboundMap.size() != outboundItemIds.size()) {
            throw business("退货明细来源销售出库明细不能为空、重复或已失效");
        }

        List<Long> derivedOrderItemIds = outboundMap.values().stream()
                .map(SalesOutboundItem::getSourceSalesOrderItemId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, SalesOrderItem> sourceOrderItemMap =
                sourceService.loadSourceSalesOrderItemMap(derivedOrderItemIds);

        Set<Long> orderIds = new LinkedHashSet<>();
        Map<Long, Integer> requestQuantityByOutboundItem = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            SalesReturnItem item = items.get(i);
            int lineNo = i + 1;
            SalesOutboundItem sourceOutbound = outboundMap.get(item.getSourceSalesOutboundItemId());
            assertSourceOutboundAudited(sourceOutbound, lineNo);
            Long derivedOrderItemId = sourceOutbound.getSourceSalesOrderItemId();
            if (derivedOrderItemId == null) {
                throw business("第" + lineNo + "行来源销售出库明细缺少销售订单明细关联");
            }
            if (item.getSourceSalesOrderItemId() != null
                    && !Objects.equals(item.getSourceSalesOrderItemId(), derivedOrderItemId)) {
                throw business("第" + lineNo + "行来源销售订单明细与来源销售出库明细不一致");
            }
            SalesOrderItem sourceOrderItem = sourceOrderItemMap.get(derivedOrderItemId);
            if (sourceOrderItem == null || sourceOrderItem.getSalesOrder() == null) {
                throw business("第" + lineNo + "行来源销售订单明细不存在");
            }
            SalesOrder sourceOrder = sourceOrderItem.getSalesOrder();
            if (sourceOrder.getId() != null) {
                orderIds.add(sourceOrder.getId());
            }
            if (item.getSourceFreightBillId() != null) {
                assertFreightBillContainsOrder(item.getSourceFreightBillId(), sourceOrder.getId(), lineNo);
            }
            requestQuantityByOutboundItem.merge(
                    item.getSourceSalesOutboundItemId(),
                    quantity(item.getQuantity()),
                    Integer::sum
            );
        }

        if (orderIds.size() != 1) {
            throw business("一张销售退货单只能来源于一张销售订单");
        }

        assertNoOverReturn(salesReturn.getId(), outboundMap, lineOutboundItemId, requestQuantityByOutboundItem);
    }

    private void assertSourceOutboundAudited(SalesOutboundItem sourceOutbound, int lineNo) {
        if (sourceOutbound == null) {
            throw business("第" + lineNo + "行来源销售出库明细不存在");
        }
        SalesOutbound outbound = sourceOutbound.getSalesOutbound();
        if (outbound == null || outbound.isDeletedFlag()) {
            throw business("第" + lineNo + "行来源销售出库单不存在或已删除");
        }
        if (!StatusConstants.AUDITED.equals(normalize(outbound.getStatus()))) {
            throw business("第" + lineNo + "行来源销售出库单未审核，不能作为退货来源");
        }
    }

    private void assertFreightBillContainsOrder(Long freightBillId, Long salesOrderId, int lineNo) {
        FreightBill bill = freightBillRepository.findById(freightBillId).orElse(null);
        if (bill == null || bill.isDeletedFlag()) {
            throw business("第" + lineNo + "行来源物流单不存在或已删除");
        }
        if (!StatusConstants.AUDITED.equals(normalize(bill.getStatus()))) {
            throw business("第" + lineNo + "行来源物流单未审核，不能作为退货来源");
        }
        if (salesOrderId == null) {
            throw business("第" + lineNo + "行无法确认来源销售订单，不能校验物流单");
        }
        boolean contains = freightBillSourceOrderRepository.findActiveBySourceOrderId(salesOrderId).stream()
                .map(FreightBillSourceOrder::getFreightBill)
                .filter(Objects::nonNull)
                .map(FreightBill::getId)
                .anyMatch(freightBillId::equals);
        if (!contains) {
            throw business("第" + lineNo + "行来源物流单未包含该销售订单");
        }
    }

    private void assertNoOverReturn(Long currentReturnId,
                                    Map<Long, SalesOutboundItem> outboundMap,
                                    Map<Integer, Long> lineOutboundItemId,
                                    Map<Long, Integer> requestQuantityByOutboundItem) {
        Map<Long, Long> auditedReturnedByOutboundItem = summarizeAuditedReturned(
                requestQuantityByOutboundItem.keySet(), currentReturnId);
        for (Map.Entry<Long, Integer> entry : requestQuantityByOutboundItem.entrySet()) {
            SalesOutboundItem sourceOutbound = outboundMap.get(entry.getKey());
            int sourceQuantity = quantity(sourceOutbound.getQuantity());
            long alreadyReturned = auditedReturnedByOutboundItem.getOrDefault(entry.getKey(), 0L);
            long total = alreadyReturned + entry.getValue();
            if (total > sourceQuantity) {
                int lineNo = lineOutboundItemId.entrySet().stream()
                        .filter(e -> e.getValue().equals(entry.getKey()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(0);
                long remaining = Math.max(sourceQuantity - alreadyReturned, 0);
                throw business("第" + lineNo + "行退货数量超过来源出库可退数量，剩余可退 " + remaining + " 件");
            }
        }
    }

    private Map<Long, Long> summarizeAuditedReturned(Collection<Long> outboundItemIds, Long currentReturnId) {
        if (outboundItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> summary = new LinkedHashMap<>();
        salesReturnItemRepository
                .summarizeAuditedQuantityBySourceOutboundItemIds(
                        outboundItemIds, StatusConstants.AUDITED, currentReturnId)
                .forEach(row -> summary.put(
                        row.getSourceSalesOutboundItemId(),
                        row.getTotalQuantity() == null ? 0L : row.getTotalQuantity()));
        return summary;
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
