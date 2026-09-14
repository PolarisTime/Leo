package com.leo.erp.sales.returns.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.sales.returns.web.dto.SalesReturnItemRequest;
import com.leo.erp.sales.returns.web.dto.SalesReturnRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 销售退货明细应用：从来源销售出库明细复制物料/仓库/批次快照，并计算重量与金额。
 * 金额口径为 重量（吨） × 单价，与销售订单/销售出库一致；总金额为各明细金额之和，总重量为各明细重量之和。
 */
@Service
public class SalesReturnApplyService {

    private final SalesReturnSourceService sourceService;

    public SalesReturnApplyService(SalesReturnSourceService sourceService) {
        this.sourceService = sourceService;
    }

    void applyItems(SalesReturn entity, SalesReturnRequest request, LongSupplier nextIdSupplier) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<SalesReturnItem> managedItems = entity.getItems();
        List<SalesReturnItem> items = ManagedEntityItemSupport.syncById(
                new ArrayList<>(managedItems),
                request.items(),
                SalesReturnItem::getId,
                SalesReturnItemRequest::id,
                SalesReturnItem::new,
                nextIdSupplier,
                SalesReturnItem::setId
        );
        Map<Long, SalesOutboundItem> sourceOutboundMap = sourceService.loadSourceOutboundItemMap(
                request.items().stream().map(SalesReturnItemRequest::sourceSalesOutboundItemId).toList());
        Map<Long, SalesOrderItem> sourceOrderItemMap = sourceService.loadSourceSalesOrderItemMap(
                request.items().stream().map(SalesReturnItemRequest::sourceSalesOutboundItemId).toList().stream()
                        .map(sourceOutboundMap::get)
                        .filter(Objects::nonNull)
                        .map(SalesOutboundItem::getSourceSalesOrderItemId)
                        .toList());

        LinkedHashSet<String> orderNos = new LinkedHashSet<>();
        LinkedHashSet<Long> customerIds = new LinkedHashSet<>();
        LinkedHashSet<String> customerNames = new LinkedHashSet<>();
        LinkedHashSet<Long> projectIds = new LinkedHashSet<>();
        LinkedHashSet<String> projectNames = new LinkedHashSet<>();
        LinkedHashSet<Long> warehouseIds = new LinkedHashSet<>();
        LinkedHashSet<String> warehouseNames = new LinkedHashSet<>();
        LinkedHashSet<Long> settlementCompanyIds = new LinkedHashSet<>();
        LinkedHashSet<String> settlementCompanyNames = new LinkedHashSet<>();

        for (int i = 0; i < request.items().size(); i++) {
            SalesReturnItemRequest source = request.items().get(i);
            SalesReturnItem item = items.get(i);
            int lineNo = i + 1;
            SalesOutboundItem sourceOutbound = sourceService.requireSourceOutboundItem(
                    sourceOutboundMap, source.sourceSalesOutboundItemId(), lineNo);
            applyItem(entity, source, item, lineNo, sourceOutbound);
            totalWeight = totalWeight.add(item.getWeightTon());
            totalAmount = totalAmount.add(item.getAmount());

            collect(orderNos, sourceOutbound.getSalesOutbound() == null
                    ? null : sourceOutbound.getSalesOutbound().getSalesOrderNo());
            addIfPresent(warehouseIds, sourceOutbound.getWarehouseId());
            addIfPresent(warehouseNames, sourceOutbound.getWarehouseName());
            addIfPresent(settlementCompanyIds, sourceOutbound.getSettlementCompanyId());
            addIfPresent(settlementCompanyNames, sourceOutbound.getSettlementCompanyName());
            SalesOrderItem sourceOrderItem = sourceOutbound.getSourceSalesOrderItemId() == null
                    ? null
                    : sourceOrderItemMap.get(sourceOutbound.getSourceSalesOrderItemId());
            if (sourceOrderItem != null && sourceOrderItem.getSalesOrder() != null) {
                addIfPresent(customerIds, sourceOrderItem.getSalesOrder().getCustomerId());
                addIfPresent(customerNames, sourceOrderItem.getSalesOrder().getCustomerName());
                addIfPresent(projectIds, sourceOrderItem.getSalesOrder().getProjectId());
                addIfPresent(projectNames, sourceOrderItem.getSalesOrder().getProjectName());
            }
        }

        managedItems.clear();
        managedItems.addAll(items);
        managedItems.sort(Comparator.comparing(SalesReturnItem::getLineNo));

        entity.setSalesOrderNo(resolveSingleText(orderNos, trimToNull(request.salesOrderNo())));
        entity.setCustomerId(resolveSingleId(customerIds, request.customerId(), "客户"));
        entity.setCustomerName(resolveSingleText(customerNames, request.customerName()));
        entity.setProjectId(resolveSingleId(projectIds, request.projectId(), "项目"));
        entity.setProjectName(resolveSingleText(projectNames, request.projectName()));
        entity.setWarehouseId(resolveSingleId(warehouseIds, request.warehouseId(), "仓库"));
        entity.setWarehouseName(resolveSingleText(warehouseNames, request.warehouseName()));
        applyHeaderSettlementCompany(entity, settlementCompanyIds, settlementCompanyNames);
        entity.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        entity.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
    }

    private void applyItem(SalesReturn entity,
                           SalesReturnItemRequest source,
                           SalesReturnItem item,
                           int lineNo,
                           SalesOutboundItem sourceOutbound) {
        item.setSalesReturn(entity);
        item.setLineNo(lineNo);
        item.setSourceSalesOutboundItemId(sourceOutbound.getId());
        item.setSourceSalesOrderItemId(sourceOutbound.getSourceSalesOrderItemId());
        item.setSourceFreightBillId(source.sourceFreightBillId());
        item.setMaterialId(sourceOutbound.getMaterialId());
        item.setMaterialCode(sourceOutbound.getMaterialCode());
        item.setBrand(sourceOutbound.getBrand());
        item.setCategory(sourceOutbound.getCategory());
        item.setMaterial(sourceOutbound.getMaterial());
        item.setSpec(sourceOutbound.getSpec());
        item.setLength(sourceOutbound.getLength());
        item.setUnit(sourceOutbound.getUnit());
        item.setSettlementCompanyId(sourceOutbound.getSettlementCompanyId());
        item.setSettlementCompanyName(sourceOutbound.getSettlementCompanyName());
        item.setWarehouseId(sourceOutbound.getWarehouseId());
        item.setWarehouseName(sourceOutbound.getWarehouseName());
        item.setBatchNo(sourceOutbound.getBatchNo());

        int quantity = source.quantity() == null ? 0 : source.quantity();
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行退货数量必须大于0");
        }
        item.setQuantity(quantity);
        item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
        BigDecimal pieceWeightTon = TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        item.setPieceWeightTon(pieceWeightTon);
        item.setPiecesPerBundle(source.piecesPerBundle());
        BigDecimal weightTon = source.weightTon() == null
                ? TradeItemCalculator.calculateWeightTon(quantity, pieceWeightTon)
                : TradeItemCalculator.scaleWeightTon(source.weightTon());
        item.setWeightTon(weightTon);
        BigDecimal unitPrice = TradeItemCalculator.scaleAmount(source.unitPrice());
        item.setUnitPrice(unitPrice);
        item.setAmount(TradeItemCalculator.scaleAmount(weightTon.multiply(unitPrice)));
    }

    private void applyHeaderSettlementCompany(SalesReturn entity,
                                              LinkedHashSet<Long> ids,
                                              LinkedHashSet<String> names) {
        Long id = resolveSingleId(ids, null, "结算主体");
        String name = resolveSingleText(names, null);
        entity.setSettlementCompanyId(id);
        entity.setSettlementCompanyName(name);
    }

    private Long resolveSingleId(LinkedHashSet<Long> values, Long fallback, String fieldName) {
        if (values.size() > 1) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "来源销售订单存在不同" + fieldName + "，不能合并生成销售退货单"
            );
        }
        return values.isEmpty() ? fallback : values.iterator().next();
    }

    private String resolveSingleText(LinkedHashSet<String> values, String fallback) {
        if (values.size() > 1) {
            return String.join(", ", values);
        }
        return values.isEmpty() ? trimToNull(fallback) : values.iterator().next();
    }

    private void collect(LinkedHashSet<String> values, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            values.add(normalized);
        }
    }

    private void addIfPresent(LinkedHashSet<Long> values, Long value) {
        if (value != null) {
            values.add(value);
        }
    }

    private void addIfPresent(LinkedHashSet<String> values, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            values.add(normalized);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
