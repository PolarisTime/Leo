package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PurchaseInboundAllocationService {

    private final PurchaseInboundItemRepository purchaseInboundItemRepository;

    public PurchaseInboundAllocationService(PurchaseInboundItemRepository purchaseInboundItemRepository) {
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
    }

    List<Long> extractSourcePurchaseOrderItemIds(PurchaseInboundRequest request) {
        return request.items().stream()
                .map(PurchaseInboundItemRequest::sourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    AllocationContext prepareContext(List<Long> sourcePurchaseOrderItemIds, Long currentInboundId) {
        return new AllocationContext(
                loadAllocatedQuantityMap(sourcePurchaseOrderItemIds, currentInboundId),
                new HashMap<>()
        );
    }

    Map<Long, Integer> loadAllocatedQuantityMap(List<Long> sourcePurchaseOrderItemIds, Long currentInboundId) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> allocatedQuantityMap = new HashMap<>();
        purchaseInboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                        sourcePurchaseOrderItemIds,
                        currentInboundId
                )
                .forEach(summary -> allocatedQuantityMap.put(
                        summary.getSourcePurchaseOrderItemId(),
                        Math.toIntExact(summary.getTotalQuantity())
                ));
        return allocatedQuantityMap;
    }

    void validateAvailableQuantity(
            PurchaseInboundItemRequest source,
            PurchaseOrderItem sourcePurchaseOrderItem,
            int lineNo,
            AllocationContext context
    ) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不能为空");
        }
        int allocatedQuantity = context.allocatedQuantityMap().getOrDefault(sourcePurchaseOrderItemId, 0);
        int requestedQuantity = context.requestAllocatedQuantityMap().getOrDefault(sourcePurchaseOrderItemId, 0);
        int sourceQuantity = sourcePurchaseOrderItem.getQuantity() == null ? 0 : sourcePurchaseOrderItem.getQuantity();
        int lineQuantity = source.quantity() == null ? 0 : source.quantity();
        int availableQuantity = sourceQuantity - allocatedQuantity;
        if (lineQuantity + requestedQuantity > availableQuantity) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行可关联数量不足，剩余可用 " + Math.max(availableQuantity - requestedQuantity, 0) + " 件"
            );
        }
        context.requestAllocatedQuantityMap().merge(sourcePurchaseOrderItemId, lineQuantity, Integer::sum);
    }

    record AllocationContext(
            Map<Long, Integer> allocatedQuantityMap,
            Map<Long, Integer> requestAllocatedQuantityMap
    ) {
    }
}
