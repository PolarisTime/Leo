package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.InvoiceAllocationSupport.AllocationProgress;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.PrecisionConstants;
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

    SourceApplyResult applyItems(InvoiceReceipt entity,
                                 List<InvoiceReceiptItemRequest> itemRequests,
                                 String supplierCode,
                                 String supplierName,
                                 LongSupplier nextIdSupplier) {
        List<Long> sourceItemIds = extractSourceItemIds(itemRequests);
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap = loadSourcePurchaseOrderItemMap(sourceItemIds);
        AllocationSnapshots allocationSnapshots = loadAllocationSnapshots(sourceItemIds, entity.getId());
        Map<Long, AllocationProgress> allocatedProgressMap = allocationSnapshots.combined();
        Map<Long, AllocationProgress> requestProgressMap = new HashMap<>();
        SettlementCompanySnapshot settlementCompany = SettlementCompanySnapshot.EMPTY;
        SupplierIdentitySnapshot supplierIdentity = SupplierIdentitySnapshot.EMPTY;
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
                    supplierCode,
                    supplierName,
                    allocationSnapshots.invoices(),
                    allocationSnapshots.refunds(),
                    requestProgressMap
            );
            validateSourcePurchaseOrderAllocation(
                    source,
                    i + 1,
                    resolvedItem,
                    sourcePurchaseOrderItemMap,
                    allocatedProgressMap,
                    requestProgressMap
            );
            settlementCompany = mergeSettlementCompany(settlementCompany, resolvedItem, i + 1);
            supplierIdentity = mergeSupplierIdentity(supplierIdentity, resolvedItem, i + 1);
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
        return new SourceApplyResult(
                amount,
                supplierIdentity.code(),
                supplierIdentity.name(),
                settlementCompany.id(),
                settlementCompany.name(),
                allocationSnapshots.hasRefundAdjustment()
        );
    }

    SourceApplyResult applyItems(InvoiceReceipt entity,
                                 List<InvoiceReceiptItemRequest> itemRequests,
                                 String supplierName,
                                 LongSupplier nextIdSupplier) {
        return applyItems(entity, itemRequests, null, supplierName, nextIdSupplier);
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
        AllocationSnapshots allocationSnapshots = loadAllocationSnapshots(sourceItemIds, entity.getId());
        SourceSnapshots snapshots = validateSourcePurchaseOrderAllocations(
                entity.getSupplierCode(),
                entity.getSupplierName(),
                items,
                loadSourcePurchaseOrderItemMap(sourceItemIds),
                allocationSnapshots.combined(),
                allocationSnapshots.invoices(),
                allocationSnapshots.refunds()
        );
        entity.setSupplierCode(snapshots.supplierIdentity().code());
        entity.setSupplierName(snapshots.supplierIdentity().name());
        entity.setSettlementCompanyId(snapshots.settlementCompany().id());
        entity.setSettlementCompanyName(snapshots.settlementCompany().name());
        entity.setInvoiceTitle(snapshots.settlementCompany().name());
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

    private AllocationSnapshots loadAllocationSnapshots(List<Long> sourceItemIds, Long currentReceiptId) {
        if (sourceItemIds.isEmpty()) {
            return AllocationSnapshots.EMPTY;
        }
        Map<Long, AllocationProgress> invoices = new HashMap<>();
        mergeAllocationSummaries(
                invoices,
                repository.summarizeAllocatedBySourcePurchaseOrderItemIds(sourceItemIds, currentReceiptId)
        );
        Map<Long, AllocationProgress> refunds = new HashMap<>();
        mergeAllocationSummaries(
                refunds,
                repository.summarizeAuditedRefundBySourcePurchaseOrderItemIds(sourceItemIds)
        );
        Map<Long, AllocationProgress> combined = new HashMap<>(invoices);
        refunds.forEach((sourceItemId, progress) -> combined.merge(
                sourceItemId,
                progress,
                AllocationProgress::merge
        ));
        return new AllocationSnapshots(
                Map.copyOf(combined),
                Map.copyOf(invoices),
                Map.copyOf(refunds)
        );
    }

    private void mergeAllocationSummaries(
            Map<Long, AllocationProgress> target,
            List<InvoiceReceiptRepository.SourceAllocationSummary> summaries
    ) {
        for (InvoiceReceiptRepository.SourceAllocationSummary summary : summaries) {
            target.merge(
                    summary.getSourcePurchaseOrderItemId(),
                    new AllocationProgress(
                            summary.getTotalQuantity() == null ? 0L : summary.getTotalQuantity(),
                            TradeItemCalculator.safeBigDecimal(summary.getTotalWeightTon()),
                            TradeItemCalculator.safeBigDecimal(summary.getTotalAmount())
                    ),
                    AllocationProgress::merge
            );
        }
    }

    private SourceSnapshots validateSourcePurchaseOrderAllocations(
            String headerSupplierCode,
            String headerSupplierName,
            List<InvoiceReceiptItemRequest> items,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
            Map<Long, AllocationProgress> allocatedProgressMap,
            Map<Long, AllocationProgress> invoiceProgressMap,
            Map<Long, AllocationProgress> refundProgressMap
    ) {
        Map<Long, AllocationProgress> requestProgressMap = new HashMap<>();
        SettlementCompanySnapshot settlementCompany = SettlementCompanySnapshot.EMPTY;
        SupplierIdentitySnapshot supplierIdentity = SupplierIdentitySnapshot.EMPTY;
        for (int i = 0; i < items.size(); i++) {
            InvoiceReceiptItemRequest source = items.get(i);
            ResolvedInvoiceReceiptItem resolvedItem = resolveItem(
                    source,
                    sourcePurchaseOrderItemMap,
                    i + 1,
                    headerSupplierCode,
                    headerSupplierName,
                    invoiceProgressMap,
                    refundProgressMap,
                    requestProgressMap
            );
            validateSourcePurchaseOrderAllocation(
                    source,
                    i + 1,
                    resolvedItem,
                    sourcePurchaseOrderItemMap,
                    allocatedProgressMap,
                    requestProgressMap
            );
            settlementCompany = mergeSettlementCompany(settlementCompany, resolvedItem, i + 1);
            supplierIdentity = mergeSupplierIdentity(supplierIdentity, resolvedItem, i + 1);
        }
        return new SourceSnapshots(supplierIdentity, settlementCompany);
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
        long nextQuantity = Math.addExact(
                Math.addExact(allocatedProgress.quantity(), requestProgress.quantity()),
                source.quantity().longValue()
        );
        if (nextQuantity > sourcePurchaseOrderItem.getQuantity().longValue()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细可收票数量不足");
        }
        BigDecimal nextWeightTon = allocatedProgress.weightTon()
                .add(requestProgress.weightTon())
                .add(resolvedItem.weightTon());
        BigDecimal sourceWeightTon = TradeItemCalculator.calculateWeightTon(
                sourcePurchaseOrderItem.getQuantity(),
                sourcePurchaseOrderItem.getPieceWeightTon()
        );
        if (nextWeightTon.compareTo(sourceWeightTon) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细可收票吨位不足");
        }

        BigDecimal nextAmount = allocatedProgress.amount()
                .add(requestProgress.amount())
                .add(resolvedItem.amount());
        BigDecimal sourceAmount = TradeItemCalculator.calculateAmount(
                sourceWeightTon,
                sourcePurchaseOrderItem.getUnitPrice()
        );
        if (nextAmount.compareTo(sourceAmount) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细可收票金额不足");
        }

        requestProgressMap.merge(
                sourcePurchaseOrderItemId,
                new AllocationProgress(source.quantity().longValue(), resolvedItem.weightTon(), resolvedItem.amount()),
                AllocationProgress::merge
        );
    }

    private ResolvedInvoiceReceiptItem resolveItem(InvoiceReceiptItemRequest source,
                                                   Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
                                                   int lineNo,
                                                   String headerSupplierCode,
                                                   String headerSupplierName,
                                                   Map<Long, AllocationProgress> invoiceProgressMap,
                                                   Map<Long, AllocationProgress> refundProgressMap,
                                                   Map<Long, AllocationProgress> requestProgressMap) {
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
        validateSourcePurchaseOrder(sourcePurchaseOrder, headerSupplierCode, headerSupplierName, lineNo);
        BigDecimal pieceWeightTon = TradeItemCalculator.scaleWeightTon(sourcePurchaseOrderItem.getPieceWeightTon());
        BigDecimal unitPrice = TradeItemCalculator.scaleAmount(sourcePurchaseOrderItem.getUnitPrice());
        AllocationProgress refundProgress = refundProgressMap.getOrDefault(
                sourcePurchaseOrderItemId,
                AllocationProgress.EMPTY
        );
        AllocationProgress receiptProgress = invoiceProgressMap.getOrDefault(
                sourcePurchaseOrderItemId,
                AllocationProgress.EMPTY
        ).merge(requestProgressMap.getOrDefault(sourcePurchaseOrderItemId, AllocationProgress.EMPTY));
        ResolvedAllocation allocation = resolveRemainingAllocation(
                sourcePurchaseOrderItem,
                source.quantity(),
                refundProgress,
                receiptProgress
        );
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
                BusinessDocumentValidator.trimToNull(sourcePurchaseOrder.getSupplierCode()),
                BusinessDocumentValidator.trimToNull(sourcePurchaseOrder.getSupplierName()),
                sourcePurchaseOrder.getSettlementCompanyId(),
                BusinessDocumentValidator.trimToNull(sourcePurchaseOrder.getSettlementCompanyName()),
                TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()),
                pieceWeightTon,
                sourcePurchaseOrderItem.getPiecesPerBundle(),
                unitPrice,
                allocation.weightTon(),
                allocation.amount()
        );
    }

    private ResolvedAllocation resolveRemainingAllocation(PurchaseOrderItem sourceItem,
                                                          Integer requestedQuantity,
                                                          AllocationProgress refundProgress,
                                                          AllocationProgress receiptProgress) {
        long sourceQuantity = sourceItem.getQuantity() == null ? 0L : sourceItem.getQuantity().longValue();
        long requested = requestedQuantity == null ? 0L : requestedQuantity.longValue();
        long remainingQuantity = Math.max(sourceQuantity - refundProgress.quantity(), 0L);
        BigDecimal sourceWeightTon = TradeItemCalculator.calculateWeightTon(
                sourceItem.getQuantity(),
                sourceItem.getPieceWeightTon()
        );
        BigDecimal sourceAmount = TradeItemCalculator.calculateAmount(sourceWeightTon, sourceItem.getUnitPrice());
        BigDecimal remainingWeightTon = TradeItemCalculator.scaleWeightTon(
                sourceWeightTon.subtract(refundProgress.weightTon()).max(BigDecimal.ZERO)
        );
        BigDecimal remainingAmount = TradeItemCalculator.scaleAmount(
                sourceAmount.subtract(refundProgress.amount()).max(BigDecimal.ZERO)
        );
        if (requested <= 0L || remainingQuantity <= 0L) {
            return ResolvedAllocation.EMPTY;
        }
        boolean hasUnquantifiedReceiptValue = receiptProgress.quantity() == 0L
                && (receiptProgress.weightTon().compareTo(BigDecimal.ZERO) > 0
                || receiptProgress.amount().compareTo(BigDecimal.ZERO) > 0);
        if (!hasUnquantifiedReceiptValue
                && Math.addExact(receiptProgress.quantity(), requested) == remainingQuantity) {
            return new ResolvedAllocation(
                    TradeItemCalculator.scaleWeightTon(
                            remainingWeightTon.subtract(receiptProgress.weightTon()).max(BigDecimal.ZERO)
                    ),
                    TradeItemCalculator.scaleAmount(
                            remainingAmount.subtract(receiptProgress.amount()).max(BigDecimal.ZERO)
                    )
            );
        }
        BigDecimal requestedShare = BigDecimal.valueOf(requested);
        BigDecimal availableQuantity = BigDecimal.valueOf(remainingQuantity);
        BigDecimal weightTon = remainingWeightTon.multiply(requestedShare).divide(
                availableQuantity,
                PrecisionConstants.WEIGHT_SCALE,
                PrecisionConstants.DEFAULT_ROUNDING
        );
        BigDecimal amount = remainingAmount.multiply(requestedShare).divide(
                availableQuantity,
                PrecisionConstants.AMOUNT_SCALE,
                PrecisionConstants.DEFAULT_ROUNDING
        );
        return new ResolvedAllocation(weightTon, amount);
    }

    private void validateSourcePurchaseOrder(PurchaseOrder sourcePurchaseOrder,
                                             String headerSupplierCode,
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
        if (BusinessDocumentValidator.trimToNull(headerSupplierCode) != null) {
            BusinessDocumentValidator.requireSameText(
                    headerSupplierCode,
                    sourcePurchaseOrder.getSupplierCode(),
                    "第" + lineNo + "行来源采购订单供应商编码与收票单不一致"
            );
        }
    }

    private SupplierIdentitySnapshot mergeSupplierIdentity(SupplierIdentitySnapshot current,
                                                            ResolvedInvoiceReceiptItem item,
                                                            int lineNo) {
        SupplierIdentitySnapshot next = new SupplierIdentitySnapshot(item.supplierCode(), item.supplierName());
        if (next.code() == null || next.name() == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行来源采购订单供应商身份不完整"
            );
        }
        if (current.isEmpty()) {
            return next;
        }
        if (!current.equals(next)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行来源采购订单供应商与收票单不一致"
            );
        }
        return current;
    }

    private SettlementCompanySnapshot mergeSettlementCompany(SettlementCompanySnapshot current,
                                                              ResolvedInvoiceReceiptItem item,
                                                              int lineNo) {
        SettlementCompanySnapshot next = new SettlementCompanySnapshot(
                item.settlementCompanyId(),
                item.settlementCompanyName()
        );
        if (next.id() == null || next.name() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行来源采购订单结算主体不完整");
        }
        if (current.isEmpty()) {
            return next;
        }
        if (!current.equals(next)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行来源采购订单结算主体与收票单不一致");
        }
        return current;
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
            String supplierCode,
            String supplierName,
            Long settlementCompanyId,
            String settlementCompanyName,
            String quantityUnit,
            BigDecimal pieceWeightTon,
            Integer piecesPerBundle,
            BigDecimal unitPrice,
            BigDecimal weightTon,
            BigDecimal amount
    ) {
    }

    private record ResolvedAllocation(BigDecimal weightTon, BigDecimal amount) {

        private static final ResolvedAllocation EMPTY = new ResolvedAllocation(
                TradeItemCalculator.scaleWeightTon(BigDecimal.ZERO),
                TradeItemCalculator.scaleAmount(BigDecimal.ZERO)
        );
    }

    private record AllocationSnapshots(
            Map<Long, AllocationProgress> combined,
            Map<Long, AllocationProgress> invoices,
            Map<Long, AllocationProgress> refunds
    ) {

        private static final AllocationSnapshots EMPTY = new AllocationSnapshots(
                Map.of(),
                Map.of(),
                Map.of()
        );

        boolean hasRefundAdjustment() {
            return refunds.values().stream().anyMatch(progress ->
                    progress.quantity() > 0L
                            || progress.weightTon().compareTo(BigDecimal.ZERO) > 0
                            || progress.amount().compareTo(BigDecimal.ZERO) > 0
            );
        }
    }

    record SourceApplyResult(
            BigDecimal amount,
            String supplierCode,
            String supplierName,
            Long settlementCompanyId,
            String settlementCompanyName,
            boolean refundAdjusted
    ) {
        SourceApplyResult(BigDecimal amount,
                          String supplierCode,
                          String supplierName,
                          Long settlementCompanyId,
                          String settlementCompanyName) {
            this(amount, supplierCode, supplierName, settlementCompanyId, settlementCompanyName, false);
        }

        SourceApplyResult(BigDecimal amount,
                          Long settlementCompanyId,
                          String settlementCompanyName) {
            this(amount, null, null, settlementCompanyId, settlementCompanyName, false);
        }
    }

    private record SourceSnapshots(
            SupplierIdentitySnapshot supplierIdentity,
            SettlementCompanySnapshot settlementCompany
    ) {
    }

    private record SupplierIdentitySnapshot(String code, String name) {

        private static final SupplierIdentitySnapshot EMPTY = new SupplierIdentitySnapshot(null, null);

        boolean isEmpty() {
            return code == null && name == null;
        }
    }

    private record SettlementCompanySnapshot(Long id, String name) {

        private static final SettlementCompanySnapshot EMPTY = new SettlementCompanySnapshot(null, null);

        boolean isEmpty() {
            return id == null && name == null;
        }
    }
}
