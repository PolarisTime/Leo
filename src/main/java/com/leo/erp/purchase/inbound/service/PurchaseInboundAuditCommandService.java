package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundAuditRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundAuditResponse;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class PurchaseInboundAuditCommandService {

    private final PurchaseInboundRepository inboundRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemQueryService purchaseOrderItemQueryService;
    private final PurchaseInboundAllocationService allocationService;
    private final PurchaseInboundService purchaseInboundService;

    public PurchaseInboundAuditCommandService(
            PurchaseInboundRepository inboundRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemQueryService purchaseOrderItemQueryService,
            PurchaseInboundAllocationService allocationService,
            PurchaseInboundService purchaseInboundService
    ) {
        this.inboundRepository = inboundRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemQueryService = purchaseOrderItemQueryService;
        this.allocationService = allocationService;
        this.purchaseInboundService = purchaseInboundService;
    }

    @Transactional
    public PurchaseInboundAuditResponse audit(Long inboundId, PurchaseInboundAuditRequest request) {
        PurchaseInbound inbound = inboundRepository.findByIdAndDeletedFlagFalse(inboundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "采购入库不存在"));
        lockSourcePurchaseOrder(inbound);
        PurchaseInboundResponse inboundResponse = purchaseInboundService.audit(
                inboundId,
                request.overToleranceConfirmations()
        );
        return new PurchaseInboundAuditResponse(inboundResponse);
    }

    private void lockSourcePurchaseOrder(PurchaseInbound inbound) {
        List<Long> sourceItemIds = inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (sourceItemIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购入库缺少来源采购订单明细");
        }
        List<PurchaseOrderItem> sourceItems = purchaseOrderItemQueryService.findActiveByIdIn(sourceItemIds);
        if (sourceItems.size() != sourceItemIds.size()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购入库的来源采购订单明细已失效");
        }
        List<Long> sourceOrderIds = sourceItems.stream()
                .map(PurchaseOrderItem::getPurchaseOrder)
                .filter(Objects::nonNull)
                .map(PurchaseOrder::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (sourceOrderIds.size() != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "一张采购入库单必须且只能关联一张采购订单");
        }
        PurchaseOrder sourceOrder = purchaseOrderRepository
                .findByIdAndDeletedFlagFalseForUpdate(sourceOrderIds.getFirst())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "来源采购订单不存在"));
        assertFullyAllocated(sourceOrder);
    }

    private void assertFullyAllocated(PurchaseOrder sourceOrder) {
        List<Long> sourceItemIds = sourceOrder.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (sourceItemIds.size() != sourceOrder.getItems().size()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购订单存在无效商品明细，不能审核采购入库");
        }
        var allocatedQuantityMap = allocationService.loadAllocatedQuantityMap(sourceItemIds, null);
        boolean fullyAllocated = sourceOrder.getItems().stream().allMatch(item -> {
            int orderedQuantity = item.getQuantity() == null ? 0 : item.getQuantity();
            int allocatedQuantity = allocatedQuantityMap.getOrDefault(item.getId(), 0);
            return orderedQuantity >= 1 && allocatedQuantity == orderedQuantity;
        });
        if (!fullyAllocated) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单必须全部商品一次性完成入库，不允许分批审核"
            );
        }
    }
}
