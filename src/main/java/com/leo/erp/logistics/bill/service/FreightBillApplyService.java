package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.LongSupplier;

@Service
public class FreightBillApplyService {

    void applyItems(FreightBill entity, FreightBillRequest request, LongSupplier nextId) {
        List<FreightBillItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                FreightBillItem::getId,
                FreightBillItemRequest::id,
                FreightBillItem::new,
                nextId,
                FreightBillItem::setId
        );
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (int index = 0; index < request.items().size(); index++) {
            FreightBillItem item = items.get(index);
            FreightBillItemRequest source = request.items().get(index);
            applyItem(entity, item, source, index + 1);
            totalWeight = totalWeight.add(item.getWeightTon());
        }
        entity.getItems().sort(java.util.Comparator.comparing(FreightBillItem::getLineNo));
        entity.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        entity.setTotalFreight(totalWeight.multiply(request.unitPrice())
                .setScale(PrecisionConstants.AMOUNT_SCALE, PrecisionConstants.DEFAULT_ROUNDING));
    }

    private void applyItem(FreightBill entity,
                           FreightBillItem item,
                           FreightBillItemRequest source,
                           int lineNo) {
        item.setFreightBill(entity);
        item.setLineNo(lineNo);
        item.setSourceNo(source.sourceNo().trim());
        item.setSettlementCompanyId(source.settlementCompanyId());
        item.setSettlementCompanyName(BusinessDocumentValidator.trimToNull(source.settlementCompanyName()));
        item.setCustomerId(source.customerId());
        item.setCustomerName(source.customerName().trim());
        item.setProjectId(source.projectId());
        item.setProjectName(source.projectName().trim());
        item.setMaterialId(source.materialId());
        item.setMaterialCode(source.materialCode().trim());
        item.setMaterialName(resolveMaterialName(source));
        item.setBrand(source.brand().trim());
        item.setCategory(source.category().trim());
        item.setMaterial(source.material().trim());
        item.setSpec(source.spec().trim());
        item.setLength(BusinessDocumentValidator.trimToNull(source.length()));
        item.setQuantity(source.quantity());
        item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
        item.setPieceWeightTon(TradeItemCalculator.scaleWeightTon(source.pieceWeightTon()));
        item.setPiecesPerBundle(source.piecesPerBundle());
        item.setBatchNo(BusinessDocumentValidator.trimToNull(source.batchNo()));
        item.setWeightTon(resolveWeightTon(source));
        item.setWarehouseId(source.warehouseId());
        item.setWarehouseName(BusinessDocumentValidator.trimToNull(source.warehouseName()));
    }

    private String resolveMaterialName(FreightBillItemRequest source) {
        String explicitName = BusinessDocumentValidator.trimToNull(source.materialName());
        return explicitName != null ? explicitName : source.brand().trim();
    }

    private BigDecimal resolveWeightTon(FreightBillItemRequest source) {
        if (source.weightTon() != null) {
            return TradeItemCalculator.scaleWeightTon(source.weightTon());
        }
        return TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon());
    }
}
