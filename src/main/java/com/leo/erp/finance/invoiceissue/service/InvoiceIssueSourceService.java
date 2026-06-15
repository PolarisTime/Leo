package com.leo.erp.finance.invoiceissue.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.InvoiceAllocationSupport.AllocationProgress;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssueItem;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueItemRequest;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class InvoiceIssueSourceService {

    private final InvoiceIssueRepository repository;
    private final SalesOrderItemQueryService salesOrderItemQueryService;

    public InvoiceIssueSourceService(InvoiceIssueRepository repository,
                                     SalesOrderItemQueryService salesOrderItemQueryService) {
        this.repository = repository;
        this.salesOrderItemQueryService = salesOrderItemQueryService;
    }

    BigDecimal applyItems(InvoiceIssue entity,
                          List<InvoiceIssueItemRequest> itemRequests,
                          String customerName,
                          String projectName,
                          LongSupplier nextIdSupplier) {
        List<Long> sourceItemIds = extractSourceItemIds(itemRequests);
        Map<Long, SalesOrderItem> sourceSalesOrderItemMap = loadSourceSalesOrderItemMap(sourceItemIds);
        Map<Long, AllocationProgress> allocatedProgressMap = loadAllocatedProgressMap(sourceItemIds, entity.getId());
        Map<Long, AllocationProgress> requestProgressMap = new HashMap<>();
        List<InvoiceIssueItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                itemRequests,
                InvoiceIssueItem::getId,
                InvoiceIssueItemRequest::id,
                InvoiceIssueItem::new,
                nextIdSupplier,
                InvoiceIssueItem::setId
        );
        BigDecimal amount = BigDecimal.ZERO;
        for (int i = 0; i < itemRequests.size(); i++) {
            InvoiceIssueItemRequest source = itemRequests.get(i);
            ResolvedInvoiceIssueItem resolvedItem = resolveItem(
                    source,
                    sourceSalesOrderItemMap,
                    i + 1,
                    customerName,
                    projectName
            );
            validateSourceSalesOrderAllocation(
                    source,
                    i + 1,
                    resolvedItem,
                    sourceSalesOrderItemMap,
                    allocatedProgressMap,
                    requestProgressMap
            );
            InvoiceIssueItem item = items.get(i);
            item.setInvoiceIssue(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(resolvedItem.sourceNo());
            item.setSourceSalesOrderItemId(source.sourceSalesOrderItemId());
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
        entity.getItems().sort(java.util.Comparator.comparing(InvoiceIssueItem::getLineNo));
        return amount;
    }

    void validateExistingItemsForIssue(InvoiceIssue entity) {
        List<InvoiceIssueItemRequest> items = entity.getItems().stream()
                .map(item -> new InvoiceIssueItemRequest(
                        item.getId(),
                        item.getSourceNo(),
                        item.getSourceSalesOrderItemId(),
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
        validateSourceSalesOrderAllocations(
                entity.getCustomerName(),
                entity.getProjectName(),
                items,
                loadSourceSalesOrderItemMap(sourceItemIds),
                loadAllocatedProgressMap(sourceItemIds, entity.getId())
        );
    }

    private List<Long> extractSourceItemIds(List<InvoiceIssueItemRequest> items) {
        return items.stream()
                .map(InvoiceIssueItemRequest::sourceSalesOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private Map<Long, SalesOrderItem> loadSourceSalesOrderItemMap(List<Long> sourceItemIds) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }

        return salesOrderItemQueryService.findActiveByIdIn(sourceItemIds).stream()
                .collect(HashMap::new, (map, item) -> map.put(item.getId(), item), HashMap::putAll);
    }

    private Map<Long, AllocationProgress> loadAllocatedProgressMap(List<Long> sourceItemIds, Long currentIssueId) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }
        return repository.summarizeAllocatedBySourceSalesOrderItemIds(
                        sourceItemIds,
                        currentIssueId
                ).stream()
                .collect(HashMap::new, (map, summary) -> map.put(
                        summary.getSourceSalesOrderItemId(),
                        new AllocationProgress(
                                TradeItemCalculator.safeBigDecimal(summary.getTotalWeightTon()),
                                TradeItemCalculator.safeBigDecimal(summary.getTotalAmount())
                        )
                ), HashMap::putAll);
    }

    private void validateSourceSalesOrderAllocations(
            String headerCustomerName,
            String headerProjectName,
            List<InvoiceIssueItemRequest> items,
            Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
            Map<Long, AllocationProgress> allocatedProgressMap
    ) {
        Map<Long, AllocationProgress> requestProgressMap = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            InvoiceIssueItemRequest source = items.get(i);
            validateSourceSalesOrderAllocation(
                    source,
                    i + 1,
                    resolveItem(source, sourceSalesOrderItemMap, i + 1, headerCustomerName, headerProjectName),
                    sourceSalesOrderItemMap,
                    allocatedProgressMap,
                    requestProgressMap
            );
        }
    }

    private void validateSourceSalesOrderAllocation(InvoiceIssueItemRequest source,
                                                    int lineNo,
                                                    ResolvedInvoiceIssueItem resolvedItem,
                                                    Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                                    Map<Long, AllocationProgress> allocatedProgressMap,
                                                    Map<Long, AllocationProgress> requestProgressMap) {
        Long sourceSalesOrderItemId = source.sourceSalesOrderItemId();
        if (sourceSalesOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不能为空");
        }

        SalesOrderItem sourceSalesOrderItem = sourceSalesOrderItemMap.get(sourceSalesOrderItemId);
        if (sourceSalesOrderItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不存在");
        }

        AllocationProgress allocatedProgress = allocatedProgressMap.getOrDefault(
                sourceSalesOrderItemId,
                AllocationProgress.EMPTY
        );
        AllocationProgress requestProgress = requestProgressMap.getOrDefault(
                sourceSalesOrderItemId,
                AllocationProgress.EMPTY
        );
        BigDecimal nextWeightTon = allocatedProgress.weightTon()
                .add(requestProgress.weightTon())
                .add(resolvedItem.weightTon());
        if (nextWeightTon.compareTo(TradeItemCalculator.safeBigDecimal(sourceSalesOrderItem.getWeightTon())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细可开票吨位不足");
        }

        BigDecimal nextAmount = allocatedProgress.amount()
                .add(requestProgress.amount())
                .add(resolvedItem.amount());
        if (nextAmount.compareTo(TradeItemCalculator.safeBigDecimal(sourceSalesOrderItem.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细可开票金额不足");
        }

        requestProgressMap.merge(
                sourceSalesOrderItemId,
                new AllocationProgress(resolvedItem.weightTon(), resolvedItem.amount()),
                AllocationProgress::merge
        );
    }

    private ResolvedInvoiceIssueItem resolveItem(InvoiceIssueItemRequest source,
                                                 Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                                 int lineNo,
                                                 String headerCustomerName,
                                                 String headerProjectName) {
        Long sourceSalesOrderItemId = source.sourceSalesOrderItemId();
        if (sourceSalesOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不能为空");
        }
        SalesOrderItem sourceSalesOrderItem = sourceSalesOrderItemMap.get(sourceSalesOrderItemId);
        if (sourceSalesOrderItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不存在");
        }
        SalesOrder sourceSalesOrder = sourceSalesOrderItem.getSalesOrder();
        if (sourceSalesOrder == null || sourceSalesOrder.getOrderNo() == null || sourceSalesOrder.getOrderNo().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单不存在");
        }
        validateSourceSalesOrder(sourceSalesOrder, headerCustomerName, headerProjectName, lineNo);
        BigDecimal pieceWeightTon = TradeItemCalculator.scaleWeightTon(sourceSalesOrderItem.getPieceWeightTon());
        BigDecimal unitPrice = TradeItemCalculator.scaleAmount(sourceSalesOrderItem.getUnitPrice());
        BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), pieceWeightTon);
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, unitPrice);
        return new ResolvedInvoiceIssueItem(
                sourceSalesOrder.getOrderNo(),
                sourceSalesOrderItem.getMaterialCode(),
                sourceSalesOrderItem.getBrand(),
                sourceSalesOrderItem.getCategory(),
                sourceSalesOrderItem.getMaterial(),
                sourceSalesOrderItem.getSpec(),
                sourceSalesOrderItem.getLength(),
                sourceSalesOrderItem.getUnit(),
                sourceSalesOrderItem.getWarehouseName(),
                sourceSalesOrderItem.getBatchNo(),
                TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()),
                sourceSalesOrderItem.getPiecesPerBundle(),
                pieceWeightTon,
                unitPrice,
                weightTon,
                amount
        );
    }

    private void validateSourceSalesOrder(SalesOrder sourceSalesOrder,
                                          String headerCustomerName,
                                          String headerProjectName,
                                          int lineNo) {
        BusinessDocumentValidator.requireStatusIn(
                sourceSalesOrder.getStatus(),
                StatusConstants.INVOICEABLE_SALES_ORDER_STATUS,
                "第" + lineNo + "行来源销售订单未审核，不能开票"
        );
        BusinessDocumentValidator.requireSameText(
                headerCustomerName,
                sourceSalesOrder.getCustomerName(),
                "第" + lineNo + "行来源销售订单客户与开票单不一致"
        );
        BusinessDocumentValidator.requireSameText(
                headerProjectName,
                sourceSalesOrder.getProjectName(),
                "第" + lineNo + "行来源销售订单项目与开票单不一致"
        );
    }

    private record ResolvedInvoiceIssueItem(
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
            Integer piecesPerBundle,
            BigDecimal pieceWeightTon,
            BigDecimal unitPrice,
            BigDecimal weightTon,
            BigDecimal amount
    ) {
    }
}
