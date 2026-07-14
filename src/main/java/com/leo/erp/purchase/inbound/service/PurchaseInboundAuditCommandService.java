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
import com.leo.erp.purchase.order.service.PurchaseOrderCompletionService;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderCompletionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class PurchaseInboundAuditCommandService {

    private final PurchaseInboundRepository inboundRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemQueryService purchaseOrderItemQueryService;
    private final PurchaseInboundService purchaseInboundService;
    private final PurchaseOrderCompletionService purchaseOrderCompletionService;

    public PurchaseInboundAuditCommandService(
            PurchaseInboundRepository inboundRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemQueryService purchaseOrderItemQueryService,
            PurchaseInboundService purchaseInboundService,
            PurchaseOrderCompletionService purchaseOrderCompletionService
    ) {
        this.inboundRepository = inboundRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemQueryService = purchaseOrderItemQueryService;
        this.purchaseInboundService = purchaseInboundService;
        this.purchaseOrderCompletionService = purchaseOrderCompletionService;
    }

    @Transactional
    public PurchaseInboundAuditResponse audit(Long inboundId, PurchaseInboundAuditRequest request) {
        PurchaseInbound inbound = inboundRepository.findByIdAndDeletedFlagFalse(inboundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "采购入库不存在"));
        PurchaseOrder sourceOrder = lockSourcePurchaseOrder(inbound);
        PurchaseInboundResponse inboundResponse = purchaseInboundService.audit(
                inboundId,
                request.overToleranceConfirmations()
        );
        PurchaseOrderCompletionResponse completionResponse = Boolean.TRUE.equals(request.closePurchaseOrder())
                ? purchaseOrderCompletionService.completePurchaseOrder(sourceOrder.getId())
                : null;
        return new PurchaseInboundAuditResponse(inboundResponse, completionResponse);
    }

    private PurchaseOrder lockSourcePurchaseOrder(PurchaseInbound inbound) {
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
        return purchaseOrderRepository.findByIdAndDeletedFlagFalseForUpdate(sourceOrderIds.getFirst())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "来源采购订单不存在"));
    }
}
