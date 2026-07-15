package com.leo.erp.purchase.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

@Service
public class PurchaseItemPieceWeightAppServiceImpl implements PurchaseItemPieceWeightAppService {

    private final PurchaseOrderItemPieceWeightService pieceWeightService;
    private final PurchaseOrderItemQueryService orderItemQueryService;
    private final PurchaseInboundItemQueryService inboundItemQueryService;

    public PurchaseItemPieceWeightAppServiceImpl(
            PurchaseOrderItemPieceWeightService pieceWeightService,
            PurchaseOrderItemQueryService orderItemQueryService,
            PurchaseInboundItemQueryService inboundItemQueryService) {
        this.pieceWeightService = pieceWeightService;
        this.orderItemQueryService = orderItemQueryService;
        this.inboundItemQueryService = inboundItemQueryService;
    }

    @Override
    public BigDecimal allocateForSalesOrderItem(
            Long sourcePurchaseOrderItemId, Integer quantity, Long salesOrderItemId, int lineNo) {
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItemId != null
                ? orderItemQueryService.findActiveByIdIn(java.util.List.of(sourcePurchaseOrderItemId))
                        .stream().findFirst().orElse(null)
                : null;
        if (sourceItem == null) {
            return BigDecimal.ZERO;
        }
        return pieceWeightService.allocateForSalesOrderItem(sourceItem, quantity, salesOrderItemId, lineNo);
    }

    @Override
    public BigDecimal allocateForInboundSourceSalesOrderItem(
            Long sourceInboundItemId, Integer quantity, Long salesOrderItemId, int lineNo) {
        PurchaseInboundItem inboundItem = inboundItemQueryService.requireActiveById(sourceInboundItemId);
        Long sourcePurchaseOrderItemId = inboundItem.getSourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行采购入库明细缺少来源采购明细");
        }
        return allocateForSalesOrderItem(sourcePurchaseOrderItemId, quantity, salesOrderItemId, lineNo);
    }

    @Override
    public void releaseSalesOrderItems(Collection<Long> salesOrderItemIds) {
        pieceWeightService.releaseSalesOrderItems(salesOrderItemIds);
    }

    @Override
    public Map<Long, BigDecimal> summarizeRemainingWeightByPurchaseOrderItemIds(
            Collection<Long> purchaseOrderItemIds) {
        return pieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds);
    }
}
