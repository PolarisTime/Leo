package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.InvoiceAllocationSupport.AllocationProgress;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceiptItem;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptItemRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class InvoiceReceiptSourceService {

    private final InvoiceReceiptRepository repository;
    private final PurchaseOrderItemQueryService purchaseOrderItemQueryService;

    public InvoiceReceiptSourceService(InvoiceReceiptRepository repository,
                                       PurchaseOrderItemQueryService purchaseOrderItemQueryService) {
        this.repository = repository;
        this.purchaseOrderItemQueryService = purchaseOrderItemQueryService;
    }

    BigDecimal applyItems(InvoiceReceipt entity,
                          List<InvoiceReceiptItemRequest> itemRequests,
                          String supplierName,
                          LongSupplier nextIdSupplier) {
        List<Long> sourceItemIds = extractSourceItemIds(itemRequests);
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap = loadSourcePurchaseOrderItemMap(sourceItemIds);
        Map<Long, AllocationProgress> allocatedProgressMap = loadAllocatedProgressMap(sourceItemIds, entity.getId());
        Map<Long, AllocationProgress> requestProgressMap = new HashMap<>();
        List<InvoiceReceiptItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                itemRequests,
                InvoiceReceiptItem::getId,
                InvoiceReceiptItemRequest::id,
                InvoiceReceiptItem::new,
                nextIdSupplier,
                InvoiceReceiptItem::setId
        );
        BigDecimal amount = BigDecimal.ZERO;
        for (int i = 0; i < itemRequests.size(); i++) {
            InvoiceReceiptItemRequest source = itemRequests.get(i);
            ResolvedInvoiceReceiptItem resolvedItem = resolveItem(
                    source,
                    sourcePurchaseOrderItemMap,
                    i + 1,
                    supplierName
            );
            validateSourcePurchaseOrderAllocation(
                    source,
                    i + 1,
                    resolvedItem,
                    sourcePurchaseOrderItemMap,
                    allocatedProgressMap,
                    requestProgressMap
            );
            InvoiceReceiptItem item = items.get(i);
            item.setInvoiceReceipt(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(resolvedItem.sourceNo());
            item.setSourcePurchaseOrderItemId(source.sourcePurchaseOrderItemId());
            item.setMaterialCode(resolvedItem.materialCode());
            item.setBrand(resolvedItem.brand());
            item.setCategory(resolvedItem.category());
            item.setMaterial(resolvedItem.material());
            item.setSpec(resolvedItem.spec());
            item.setLength(resolvedItem.length());
            item.setUnit(resolvedItem.unit());
            item.setWarehouseName(resolvedItem.warehouseName());
            item.setBatchNo(resolvedItem.batchNo());
            item.setQuantity(source.quantity());
            item.setQuantityUnit(resolvedItem.quantityUnit());
            item.setPieceWeightTon(resolvedItem.pieceWeightTon());
            item.setPiecesPerBundle(resolvedItem.piecesPerBundle());
            item.setWeightTon(resolvedItem.weightTon());
            item.setUnitPrice(resolvedItem.unitPrice());
            BigDecimal lineAmount = resolvedItem.amount();
            item.setAmount(lineAmount);
            amount = amount.add(lineAmount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(InvoiceReceiptItem::getLineNo));
        return amount;
    }

    void validateExistingItemsForReceipt(InvoiceReceipt entity) {
        List<InvoiceReceiptItemRequest> items = entity.getItems().stream()
                .map(item -> new InvoiceReceiptItemRequest(
                        item.getId(),
                        item.getSourceNo(),
                        item.getSourcePurchaseOrderItemId(),
                        item.getMaterialCode(),
                        item.getBrand(),
                        item.getCategory(),
                        item.getMaterial(),
                        item.getSpec(),
                        item.getLength(),
                        item.getUnit(),
                        item.getWarehouseName(),
                        item.getBatchNo(),
                        item.getQuantity(),
                        item.getQuantityUnit(),
                        item.getPieceWeightTon(),
                        item.getPiecesPerBundle(),
                        item.getWeightTon(),
                        item.getUnitPrice(),
                        item.getAmount()
                ))
                .toList();
        List<Long> sourceItemIds = extractSourceItemIds(items);
        validateSourcePurchaseOrderAllocations(
                entity.getSupplierName(),
                items,
                loadSourcePurchaseOrderItemMap(sourceItemIds),
                loadAllocatedProgressMap(sourceItemIds, entity.getId())
        );
    }

    private List<Long> extractSourceItemIds(List<InvoiceReceiptItemRequest> items) {
        return items.stream()
                .map(InvoiceReceiptItemRequest::sourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private Map<Long, PurchaseOrderItem> loadSourcePurchaseOrderItemMap(List<Long> sourceItemIds) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }
        return purchaseOrderItemQueryService.findActiveByIdIn(sourceItemIds).stream()
                .collect(HashMap::new, (map, item) -> map.put(item.getId(), item), HashMap::putAll);
    }

    private Map<Long, AllocationProgress> loadAllocatedProgressMap(List<Long> sourceItemIds, Long currentReceiptId) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }
        return repository.summarizeAllocatedBySourcePurchaseOrderItemIds(sourceItemIds, currentReceiptId).stream()
                .collect(HashMap::new, (map, summary) -> map.put(
                        summary.getSourcePurchaseOrderItemId(),
                        new AllocationProgress(
                                TradeItemCalculator.safeBigDecimal(summary.getTotalWeightTon()),
                                TradeItemCalculator.safeBigDecimal(summary.getTotalAmount())
                        )
                ), HashMap::putAll);
    }

    private void validateSourcePurchaseOrderAllocations(
            String headerSupplierName,
            List<InvoiceReceiptItemRequest> items,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
            Map<Long, AllocationProgress> allocatedProgressMap
    ) {
        Map<Long, AllocationProgress> requestProgressMap = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            InvoiceReceiptItemRequest source = items.get(i);
            validateSourcePurchaseOrderAllocation(
                    source,
                    i + 1,
                    resolveItem(source, sourcePurchaseOrderItemMap, i + 1, headerSupplierName),
                    sourcePurchaseOrderItemMap,
                    allocatedProgressMap,
                    requestProgressMap
            );
        }
    }

    private void validateSourcePurchaseOrderAllocation(InvoiceReceiptItemRequest source,
                                                       int lineNo,
                                                       ResolvedInvoiceReceiptItem resolvedItem,
                                                       Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
                                                       Map<Long, AllocationProgress> allocatedProgressMap,
                                                       Map<Long, AllocationProgress> requestProgressMap) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不能为空");
        }

        PurchaseOrderItem sourcePurchaseOrderItem = sourcePurchaseOrderItemMap.get(sourcePurchaseOrderItemId);
        if (sourcePurchaseOrderItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不存在");
        }

        AllocationProgress allocatedProgress = allocatedProgressMap.getOrDefault(
                sourcePurchaseOrderItemId,
                AllocationProgress.EMPTY
        );
        AllocationProgress requestProgress = requestProgressMap.getOrDefault(
                sourcePurchaseOrderItemId,
                AllocationProgress.EMPTY
        );
        BigDecimal nextWeightTon = allocatedProgress.weightTon()
                .add(requestProgress.weightTon())
                .add(resolvedItem.weightTon());
        if (nextWeightTon.compareTo(TradeItemCalculator.safeBigDecimal(sourcePurchaseOrderItem.getWeightTon())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细可收票吨位不足");
        }

        BigDecimal nextAmount = allocatedProgress.amount()
                .add(requestProgress.amount())
                .add(resolvedItem.amount());
        if (nextAmount.compareTo(TradeItemCalculator.safeBigDecimal(sourcePurchaseOrderItem.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细可收票金额不足");
        }

        requestProgressMap.merge(
                sourcePurchaseOrderItemId,
                new AllocationProgress(resolvedItem.weightTon(), resolvedItem.amount()),
                AllocationProgress::merge
        );
    }

    private ResolvedInvoiceReceiptItem resolveItem(InvoiceReceiptItemRequest source,
                                                   Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
                                                   int lineNo,
                                                   String headerSupplierName) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不能为空");
        }
        PurchaseOrderItem sourcePurchaseOrderItem = sourcePurchaseOrderItemMap.get(sourcePurchaseOrderItemId);
        if (sourcePurchaseOrderItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不存在");
        }
        PurchaseOrder sourcePurchaseOrder = sourcePurchaseOrderItem.getPurchaseOrder();
        if (sourcePurchaseOrder == null || sourcePurchaseOrder.getOrderNo() == null || sourcePurchaseOrder.getOrderNo().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单不存在");
        }
        validateSourcePurchaseOrder(sourcePurchaseOrder, headerSupplierName, lineNo);
        BigDecimal pieceWeightTon = TradeItemCalculator.scaleWeightTon(sourcePurchaseOrderItem.getPieceWeightTon());
        BigDecimal unitPrice = TradeItemCalculator.scaleAmount(sourcePurchaseOrderItem.getUnitPrice());
        BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), pieceWeightTon);
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, unitPrice);
        return new ResolvedInvoiceReceiptItem(
                sourcePurchaseOrder.getOrderNo(),
                sourcePurchaseOrderItem.getMaterialCode(),
                sourcePurchaseOrderItem.getBrand(),
                sourcePurchaseOrderItem.getCategory(),
                sourcePurchaseOrderItem.getMaterial(),
                sourcePurchaseOrderItem.getSpec(),
                sourcePurchaseOrderItem.getLength(),
                sourcePurchaseOrderItem.getUnit(),
                sourcePurchaseOrderItem.getWarehouseName(),
                sourcePurchaseOrderItem.getBatchNo(),
                TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()),
                pieceWeightTon,
                sourcePurchaseOrderItem.getPiecesPerBundle(),
                unitPrice,
                weightTon,
                amount
        );
    }

    private void validateSourcePurchaseOrder(PurchaseOrder sourcePurchaseOrder,
                                             String headerSupplierName,
                                             int lineNo) {
        BusinessDocumentValidator.requireStatusIn(
                sourcePurchaseOrder.getStatus(),
                StatusConstants.INVOICEABLE_PURCHASE_ORDER_STATUS,
                "第" + lineNo + "行来源采购订单未审核，不能收票"
        );
        BusinessDocumentValidator.requireSameText(
                headerSupplierName,
                sourcePurchaseOrder.getSupplierName(),
                "第" + lineNo + "行来源采购订单供应商与收票单不一致"
        );
    }

    private record ResolvedInvoiceReceiptItem(
            String sourceNo,
            String materialCode,
            String brand,
            String category,
            String material,
            String spec,
            String length,
            String unit,
            String warehouseName,
            String batchNo,
            String quantityUnit,
            BigDecimal pieceWeightTon,
            Integer piecesPerBundle,
            BigDecimal unitPrice,
            BigDecimal weightTon,
            BigDecimal amount
    ) {
    }
}
