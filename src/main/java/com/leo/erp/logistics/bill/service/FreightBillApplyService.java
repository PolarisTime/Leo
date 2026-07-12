package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

@Service
public class FreightBillApplyService {

    void applyItems(FreightBill entity,
                    FreightBillRequest request,
                    LongSupplier nextId) {
        applyItems(
                entity,
                request,
                new FreightBillSourceService.SourceValidationContext(java.util.Map.of(), java.util.Map.of()),
                nextId
        );
    }

    void applyItems(FreightBill entity,
                    FreightBillRequest request,
                    FreightBillSourceService.SourceValidationContext sourceContext,
                    LongSupplier nextId) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        LinkedHashSet<String> customerNames = new LinkedHashSet<>();
        LinkedHashSet<String> projectNames = new LinkedHashSet<>();
        List<FreightBillItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                FreightBillItem::getId,
                FreightBillItemRequest::id,
                FreightBillItem::new,
                nextId,
                FreightBillItem::setId
        );
        for (int i = 0; i < request.items().size(); i++) {
            FreightBillItemRequest source = request.items().get(i);
            FreightBillItem item = items.get(i);
            int lineNo = i + 1;
            SalesOutboundItem sourceOutboundItem = sourceContext.sourceItemAt(lineNo);
            SalesOutbound sourceOutbound = sourceContext.sourceOutboundAt(lineNo);
            if (sourceOutbound == null && sourceOutboundItem != null) {
                sourceOutbound = sourceOutboundItem.getSalesOutbound();
            }
            item.setFreightBill(entity);
            item.setLineNo(lineNo);
            item.setSourceNo(source.sourceNo());
            applySourceSnapshot(item, sourceOutbound, sourceOutboundItem);
            String customerName = sourceOutbound == null ? source.customerName() : sourceOutbound.getCustomerName();
            String projectName = sourceOutbound == null ? source.projectName() : sourceOutbound.getProjectName();
            item.setCustomerName(customerName);
            customerNames.add(customerName);
            item.setProjectName(projectName);
            projectNames.add(projectName);
            item.setMaterialCode(sourceOutboundItem == null
                    ? source.materialCode()
                    : sourceOutboundItem.getMaterialCode());
            item.setMaterialName(resolveMaterialName(source));
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setQuantity(source.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
            item.setPieceWeightTon(source.pieceWeightTon());
            item.setPiecesPerBundle(source.piecesPerBundle());
            item.setBatchNo(sourceOutboundItem == null ? source.batchNo() : sourceOutboundItem.getBatchNo());
            BigDecimal weightTon = resolveWeightTon(source, sourceOutboundItem);
            item.setWeightTon(weightTon);
            item.setWarehouseName(sourceOutboundItem == null
                    ? source.warehouseName()
                    : sourceOutboundItem.getWarehouseName());
            totalWeight = totalWeight.add(weightTon);
        }
        entity.getItems().sort(java.util.Comparator.comparing(FreightBillItem::getLineNo));
        entity.setCustomerName(resolveHeaderLabel(customerNames, "多客户"));
        entity.setProjectName(resolveHeaderLabel(projectNames, "多项目"));
        entity.setTotalWeight(totalWeight);
        entity.setTotalFreight(totalWeight.multiply(request.unitPrice()).setScale(PrecisionConstants.AMOUNT_SCALE, PrecisionConstants.DEFAULT_ROUNDING));
    }

    private String resolveHeaderLabel(Set<String> values, String multipleLabel) {
        if (values.isEmpty()) {
            return multipleLabel;
        }
        if (values.size() == 1) {
            return values.iterator().next();
        }
        return multipleLabel;
    }

    private String resolveMaterialName(FreightBillItemRequest source) {
        String explicitName = BusinessDocumentValidator.trimToNull(source.materialName());
        if (explicitName != null) {
            return explicitName;
        }
        return BusinessDocumentValidator.trimToNull(source.brand());
    }

    private BigDecimal resolveWeightTon(FreightBillItemRequest source, SalesOutboundItem sourceOutboundItem) {
        if (sourceOutboundItem != null && sourceOutboundItem.getWeightTon() != null) {
            return TradeItemCalculator.scaleWeightTon(sourceOutboundItem.getWeightTon());
        }
        return TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon());
    }

    private void applySourceSnapshot(FreightBillItem item,
                                     SalesOutbound sourceOutbound,
                                     SalesOutboundItem sourceOutboundItem) {
        if (sourceOutboundItem == null) {
            item.setSourceSalesOutboundItemId(null);
            item.setSettlementCompanyId(null);
            item.setSettlementCompanyName(null);
            item.setCustomerId(null);
            item.setProjectId(null);
            item.setMaterialId(null);
            item.setWarehouseId(null);
            return;
        }
        item.setSourceSalesOutboundItemId(sourceOutboundItem.getId());
        item.setSettlementCompanyId(sourceOutboundItem.getSettlementCompanyId());
        item.setSettlementCompanyName(sourceOutboundItem.getSettlementCompanyName());
        item.setCustomerId(sourceOutbound == null ? null : sourceOutbound.getCustomerId());
        item.setProjectId(sourceOutbound == null ? null : sourceOutbound.getProjectId());
        item.setMaterialId(sourceOutboundItem.getMaterialId());
        item.setWarehouseId(sourceOutboundItem.getWarehouseId());
    }
}
