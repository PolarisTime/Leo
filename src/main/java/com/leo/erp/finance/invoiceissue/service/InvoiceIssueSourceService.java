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

    SourceApplyResult applyItems(InvoiceIssue entity,
                                 List<InvoiceIssueItemRequest> itemRequests,
                                 Long customerId,
                                 String customerName,
                                 Long projectId,
                                 String projectName,
                                 LongSupplier nextIdSupplier) {
        List<Long> sourceItemIds = extractSourceItemIds(itemRequests);
        Map<Long, SalesOrderItem> sourceSalesOrderItemMap = loadSourceSalesOrderItemMap(sourceItemIds);
        Map<Long, AllocationProgress> allocatedProgressMap = loadAllocatedProgressMap(sourceItemIds, entity.getId());
        Map<Long, AllocationProgress> requestProgressMap = new HashMap<>();
        SettlementCompanySnapshot settlementCompany = SettlementCompanySnapshot.EMPTY;
        PartySnapshot party = PartySnapshot.EMPTY;
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
                    customerId,
                    customerName,
                    projectId,
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
            settlementCompany = mergeSettlementCompany(settlementCompany, resolvedItem, i + 1);
            party = mergeParty(party, resolvedItem, i + 1);
            InvoiceIssueItem item = items.get(i);
            item.setInvoiceIssue(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(resolvedItem.sourceNo());
            item.setSourceSalesOrderItemId(source.sourceSalesOrderItemId());
            item.setMaterialId(resolvedItem.materialId());
            item.setMaterialCode(resolvedItem.materialCode());
            item.setBrand(resolvedItem.brand());
            item.setCategory(resolvedItem.category());
            item.setMaterial(resolvedItem.material());
            item.setSpec(resolvedItem.spec());
            item.setLength(resolvedItem.length());
            item.setUnit(resolvedItem.unit());
            item.setWarehouseName(resolvedItem.warehouseName());
            item.setWarehouseId(resolvedItem.warehouseId());
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
        return new SourceApplyResult(
                amount,
                settlementCompany.id(),
                settlementCompany.name(),
                party.customerId(),
                party.projectId()
        );
    }

    SourceApplyResult applyItems(InvoiceIssue entity,
                                 List<InvoiceIssueItemRequest> itemRequests,
                                 String customerName,
                                 String projectName,
                                 LongSupplier nextIdSupplier) {
        return applyItems(entity, itemRequests, null, customerName, null, projectName, nextIdSupplier);
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
        SettlementCompanySnapshot settlementCompany = validateSourceSalesOrderAllocations(
                entity.getCustomerName(),
                entity.getProjectName(),
                items,
                loadSourceSalesOrderItemMap(sourceItemIds),
                loadAllocatedProgressMap(sourceItemIds, entity.getId())
        );
        entity.setSettlementCompanyId(settlementCompany.id());
        entity.setSettlementCompanyName(settlementCompany.name());
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
                                summary.getTotalQuantity() == null ? 0L : summary.getTotalQuantity(),
                                TradeItemCalculator.safeBigDecimal(summary.getTotalWeightTon()),
                                TradeItemCalculator.safeBigDecimal(summary.getTotalAmount())
                        )
                ), HashMap::putAll);
    }

    private SettlementCompanySnapshot validateSourceSalesOrderAllocations(
            String headerCustomerName,
            String headerProjectName,
            List<InvoiceIssueItemRequest> items,
            Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
            Map<Long, AllocationProgress> allocatedProgressMap
    ) {
        Map<Long, AllocationProgress> requestProgressMap = new HashMap<>();
        SettlementCompanySnapshot settlementCompany = SettlementCompanySnapshot.EMPTY;
        for (int i = 0; i < items.size(); i++) {
            InvoiceIssueItemRequest source = items.get(i);
            ResolvedInvoiceIssueItem resolvedItem = resolveItem(
                    source,
                    sourceSalesOrderItemMap,
                    i + 1,
                    null,
                    headerCustomerName,
                    null,
                    headerProjectName
            );
            validateSourceSalesOrderAllocation(
                    source,
                    i + 1,
                    resolvedItem,
                    sourceSalesOrderItemMap,
                    allocatedProgressMap,
                    requestProgressMap
            );
            settlementCompany = mergeSettlementCompany(settlementCompany, resolvedItem, i + 1);
        }
        return settlementCompany;
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
        long nextQuantity = Math.addExact(
                Math.addExact(allocatedProgress.quantity(), requestProgress.quantity()),
                source.quantity().longValue()
        );
        if (nextQuantity > sourceSalesOrderItem.getQuantity().longValue()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细可开票数量不足");
        }
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
                new AllocationProgress(source.quantity().longValue(), resolvedItem.weightTon(), resolvedItem.amount()),
                AllocationProgress::merge
        );
    }

    private ResolvedInvoiceIssueItem resolveItem(InvoiceIssueItemRequest source,
                                                 Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                                 int lineNo,
                                                 Long headerCustomerId,
                                                 String headerCustomerName,
                                                 Long headerProjectId,
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
        validateSourceSalesOrder(
                sourceSalesOrder,
                headerCustomerId,
                headerCustomerName,
                headerProjectId,
                headerProjectName,
                lineNo
        );
        requireSameIdentity(source.materialId(), sourceSalesOrderItem.getMaterialId(), lineNo, "商品");
        requireSameIdentity(source.warehouseId(), sourceSalesOrderItem.getWarehouseId(), lineNo, "仓库");
        BigDecimal pieceWeightTon = TradeItemCalculator.scaleWeightTon(sourceSalesOrderItem.getPieceWeightTon());
        BigDecimal unitPrice = TradeItemCalculator.scaleAmount(sourceSalesOrderItem.getUnitPrice());
        BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), pieceWeightTon);
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, unitPrice);
        return new ResolvedInvoiceIssueItem(
                sourceSalesOrder.getOrderNo(),
                sourceSalesOrder.getCustomerId(),
                sourceSalesOrder.getProjectId(),
                sourceSalesOrderItem.getMaterialId(),
                sourceSalesOrderItem.getWarehouseId(),
                sourceSalesOrderItem.getMaterialCode(),
                sourceSalesOrderItem.getBrand(),
                sourceSalesOrderItem.getCategory(),
                sourceSalesOrderItem.getMaterial(),
                sourceSalesOrderItem.getSpec(),
                sourceSalesOrderItem.getLength(),
                sourceSalesOrderItem.getUnit(),
                sourceSalesOrderItem.getWarehouseName(),
                sourceSalesOrderItem.getBatchNo(),
                sourceSalesOrder.getSettlementCompanyId(),
                BusinessDocumentValidator.trimToNull(sourceSalesOrder.getSettlementCompanyName()),
                TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()),
                sourceSalesOrderItem.getPiecesPerBundle(),
                pieceWeightTon,
                unitPrice,
                weightTon,
                amount
        );
    }

    private void validateSourceSalesOrder(SalesOrder sourceSalesOrder,
                                          Long headerCustomerId,
                                          String headerCustomerName,
                                          Long headerProjectId,
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
        requireSameIdentity(headerCustomerId, sourceSalesOrder.getCustomerId(), lineNo, "客户");
        requireSameIdentity(headerProjectId, sourceSalesOrder.getProjectId(), lineNo, "项目");
    }

    private void requireSameIdentity(Long requestedId, Long sourceId, int lineNo, String fieldName) {
        if (requestedId != null && !requestedId.equals(sourceId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行" + fieldName + "ID与来源销售订单不一致");
        }
    }

    private PartySnapshot mergeParty(PartySnapshot current,
                                     ResolvedInvoiceIssueItem item,
                                     int lineNo) {
        PartySnapshot next = new PartySnapshot(item.customerId(), item.projectId());
        if (current.isEmpty()) {
            return next;
        }
        if (!current.equals(next)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行来源销售订单客户或项目不一致");
        }
        return current;
    }

    private SettlementCompanySnapshot mergeSettlementCompany(SettlementCompanySnapshot current,
                                                             ResolvedInvoiceIssueItem item,
                                                             int lineNo) {
        SettlementCompanySnapshot next = new SettlementCompanySnapshot(
                item.settlementCompanyId(),
                item.settlementCompanyName()
        );
        if (next.isEmpty()) {
            return current;
        }
        if (current.isEmpty()) {
            return next;
        }
        if (!current.equals(next)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单结算主体与开票单不一致");
        }
        return current;
    }

    private record ResolvedInvoiceIssueItem(
            String sourceNo,
            Long customerId,
            Long projectId,
            Long materialId,
            Long warehouseId,
            String materialCode,
            String brand,
            String category,
            String material,
            String spec,
            String length,
            String unit,
            String warehouseName,
            String batchNo,
            Long settlementCompanyId,
            String settlementCompanyName,
            String quantityUnit,
            Integer piecesPerBundle,
            BigDecimal pieceWeightTon,
            BigDecimal unitPrice,
            BigDecimal weightTon,
            BigDecimal amount
    ) {
    }

    record SourceApplyResult(
            BigDecimal amount,
            Long settlementCompanyId,
            String settlementCompanyName,
            Long customerId,
            Long projectId
    ) {
        SourceApplyResult(BigDecimal amount,
                          Long settlementCompanyId,
                          String settlementCompanyName) {
            this(amount, settlementCompanyId, settlementCompanyName, null, null);
        }
    }

    private record PartySnapshot(Long customerId, Long projectId) {
        private static final PartySnapshot EMPTY = new PartySnapshot(null, null);

        boolean isEmpty() {
            return customerId == null && projectId == null;
        }
    }

    private record SettlementCompanySnapshot(Long id, String name) {

        private static final SettlementCompanySnapshot EMPTY = new SettlementCompanySnapshot(null, null);

        boolean isEmpty() {
            return id == null && name == null;
        }
    }
}
