package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class SalesOrderApplyService {

    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final SalesOrderSourceAllocationService sourceAllocationService;
    private final SalesOrderWeightResolver weightResolver;
    private final SalesOrderPurchaseAllocationService purchaseAllocationService;
    private final SalesOrderItemMapper salesOrderItemMapper;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public SalesOrderApplyService(TradeItemMaterialSupport tradeItemMaterialSupport,
                                  SalesOrderSourceAllocationService sourceAllocationService,
                                  SalesOrderWeightResolver weightResolver,
                                  SalesOrderPurchaseAllocationService purchaseAllocationService,
                                  SalesOrderItemMapper salesOrderItemMapper,
                                  WorkflowTransitionGuard workflowTransitionGuard) {
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.sourceAllocationService = sourceAllocationService;
        this.weightResolver = weightResolver;
        this.purchaseAllocationService = purchaseAllocationService;
        this.salesOrderItemMapper = salesOrderItemMapper;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    void apply(SalesOrder entity, SalesOrderRequest request, LongSupplier nextIdSupplier) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                entity.getStatus() != null ? entity.getStatus() : StatusConstants.DRAFT,
                "销售订单状态",
                StatusConstants.ALLOWED_SALES_ORDER_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "sales-order",
                entity.getStatus(),
                nextStatus,
                StatusConstants.AUDITED,
                StatusConstants.SALES_COMPLETED
        );
        applyHeader(entity, request, nextStatus);
        applyItems(entity, request, nextIdSupplier);
    }

    private void applyHeader(SalesOrder entity, SalesOrderRequest request, String nextStatus) {
        entity.setOrderNo(request.orderNo());
        entity.setPurchaseInboundNo(request.purchaseInboundNo());
        entity.setPurchaseOrderNo(request.purchaseOrderNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setCustomerCode(request.customerCode());
        entity.setProjectId(request.projectId());
        entity.setDeliveryDate(request.deliveryDate());
        entity.setSalesName(request.salesName());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());
    }

    private void applyItems(SalesOrder entity, SalesOrderRequest request, LongSupplier nextIdSupplier) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        Map<String, TradeMaterialSnapshot> materialMap = tradeItemMaterialSupport.loadMaterialMap(
                request.items().stream().map(SalesOrderItemRequest::materialCode).toList()
        );
        SalesOrderSourceContext sourceContext = sourceAllocationService.prepareContext(request, entity.getId());
        purchaseAllocationService.releaseSalesOrderItems(entity);
        sourceContext = weightResolver.withPurchaseOrderRemainingWeights(sourceContext);
        List<SalesOrderItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                SalesOrderItem::getId,
                SalesOrderItemRequest::id,
                SalesOrderItem::new,
                nextIdSupplier,
                SalesOrderItem::setId
        );
        for (int i = 0; i < request.items().size(); i++) {
            ItemTotals itemTotals = applyItem(
                    entity,
                    request.items().get(i),
                    items.get(i),
                    i + 1,
                    materialMap,
                    sourceContext
            );
            totalWeight = totalWeight.add(itemTotals.weightTon());
            totalAmount = totalAmount.add(itemTotals.amount());
        }
        entity.getItems().sort(Comparator.comparing(SalesOrderItem::getLineNo));
        entity.setPurchaseInboundNo(sourceContext.resolvePurchaseInboundNo(request.purchaseInboundNo()));
        entity.setPurchaseOrderNo(sourceContext.resolvePurchaseOrderNo(request.purchaseOrderNo()));
        entity.setTotalWeight(totalWeight);
        entity.setTotalAmount(totalAmount);
    }

    private ItemTotals applyItem(SalesOrder entity,
                                 SalesOrderItemRequest source,
                                 SalesOrderItem item,
                                 int lineNo,
                                 Map<String, TradeMaterialSnapshot> materialMap,
                                 SalesOrderSourceContext sourceContext) {
        TradeMaterialSnapshot material = materialMap.get(source.materialCode());
        sourceAllocationService.resolveSourceInbound(source, sourceContext);
        sourceAllocationService.resolveSourcePurchaseOrder(source, sourceContext);
        sourceAllocationService.validateLine(source, lineNo, sourceContext);
        BigDecimal pieceWeightTon = weightResolver.resolvePieceWeightTon(source, sourceContext);
        BigDecimal weightTon = weightResolver.resolveWeightTon(source, pieceWeightTon, sourceContext);
        salesOrderItemMapper.applyItemFields(entity, source, item, lineNo, material, weightTon, pieceWeightTon);
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.unitPrice());
        item.setAmount(amount);
        sourceAllocationService.recordAllocation(source, weightTon, sourceContext);
        return new ItemTotals(weightTon, amount);
    }

    private record ItemTotals(BigDecimal weightTon, BigDecimal amount) {
    }
}
