package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.InvoiceAllocationSupport.AllocationProgress;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.common.web.dto.InvoiceSourceCandidateItemResponse;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptSourceCandidateResponse;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvoiceReceiptCandidateService {

    private static final int BATCH_SIZE = 200;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InvoiceReceiptRepository invoiceReceiptRepository;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;

    public InvoiceReceiptCandidateService(PurchaseOrderRepository purchaseOrderRepository,
                                          InvoiceReceiptRepository invoiceReceiptRepository,
                                          ResourceRecordAccessGuard resourceRecordAccessGuard) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.invoiceReceiptRepository = invoiceReceiptRepository;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceReceiptSourceCandidateResponse> sourceCandidates(PageQuery query, PageFilter filter) {
        Specification<PurchaseOrder> specification = Specs.<PurchaseOrder>notDeleted()
                .and((root, criteriaQuery, criteriaBuilder) ->
                        root.get("status").in(StatusConstants.INVOICEABLE_PURCHASE_ORDER_STATUS))
                .and(Specs.keywordLike(filter.keyword(), "orderNo", "supplierName"))
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.dateTimeBetweenDatesIfPresent(
                        "orderDate",
                        filter.startDate(),
                        filter.endDate()
                ));
        long requestedStart = (long) query.page() * query.size();
        long requestedEnd = requestedStart + query.size();
        long candidateCount = 0L;
        List<InvoiceReceiptSourceCandidateResponse> requestedCandidates = new ArrayList<>(query.size());
        int pageIndex = 0;
        Page<PurchaseOrder> batch;
        do {
            batch = purchaseOrderRepository.findAll(
                    DataScopeContext.apply(specification),
                    sourceBatchPageable(query, pageIndex)
            );
            List<PurchaseOrder> orders = batch.getContent().stream()
                    .filter(order -> StatusConstants.INVOICEABLE_PURCHASE_ORDER_STATUS.contains(order.getStatus()))
                    .toList();
            orders.forEach(order -> resourceRecordAccessGuard.assertCurrentUserCanAccess(
                    "purchase-order",
                    "read",
                    order
            ));
            for (InvoiceReceiptSourceCandidateResponse candidate : calculateCandidates(
                    orders,
                    filter.currentRecordId()
            )) {
                if (candidateCount >= requestedStart && candidateCount < requestedEnd) {
                    requestedCandidates.add(candidate);
                }
                candidateCount++;
            }
            pageIndex++;
        } while (batch.hasNext());
        return new PageImpl<>(requestedCandidates, query.toPageable("id"), candidateCount);
    }

    private List<InvoiceReceiptSourceCandidateResponse> calculateCandidates(
            List<PurchaseOrder> orders,
            Long currentReceiptId
    ) {
        List<Long> itemIds = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(PurchaseOrderItem::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (itemIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AllocationProgress> receipts = allocationMap(
                invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(
                        itemIds,
                        currentReceiptId
                )
        );
        Map<Long, AllocationProgress> refunds = allocationMap(
                invoiceReceiptRepository.summarizeAuditedRefundBySourcePurchaseOrderItemIds(itemIds)
        );
        return orders.stream()
                .map(order -> toCandidate(order, receipts, refunds))
                .filter(candidate -> !candidate.items().isEmpty())
                .toList();
    }

    private Map<Long, AllocationProgress> allocationMap(
            List<InvoiceReceiptRepository.SourceAllocationSummary> summaries
    ) {
        Map<Long, AllocationProgress> result = new HashMap<>();
        summaries.forEach(summary -> result.put(
                summary.getSourcePurchaseOrderItemId(),
                new AllocationProgress(
                        summary.getTotalQuantity() == null ? 0L : summary.getTotalQuantity(),
                        TradeItemCalculator.safeBigDecimal(summary.getTotalWeightTon()),
                        TradeItemCalculator.safeBigDecimal(summary.getTotalAmount())
                )
        ));
        return result;
    }

    private InvoiceReceiptSourceCandidateResponse toCandidate(
            PurchaseOrder order,
            Map<Long, AllocationProgress> receipts,
            Map<Long, AllocationProgress> refunds
    ) {
        List<InvoiceSourceCandidateItemResponse> items = order.getItems().stream()
                .filter(item -> item.getId() != null)
                .map(item -> toCandidateItem(
                        item,
                        receipts.getOrDefault(item.getId(), AllocationProgress.EMPTY),
                        refunds.getOrDefault(item.getId(), AllocationProgress.EMPTY)
                ))
                .filter(item -> item.quantity() > 0)
                .toList();
        BigDecimal totalWeight = items.stream()
                .map(InvoiceSourceCandidateItemResponse::weightTon)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = items.stream()
                .map(InvoiceSourceCandidateItemResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InvoiceReceiptSourceCandidateResponse(
                order.getId(),
                order.getOrderNo(),
                order.getSupplierId(),
                order.getSupplierCode(),
                order.getSupplierName(),
                order.getSettlementCompanyId(),
                order.getSettlementCompanyName(),
                order.getOrderDate(),
                order.getBuyerName(),
                TradeItemCalculator.scaleWeightTon(totalWeight),
                TradeItemCalculator.scaleAmount(totalAmount),
                order.getStatus(),
                items
        );
    }

    private InvoiceSourceCandidateItemResponse toCandidateItem(
            PurchaseOrderItem item,
            AllocationProgress receipt,
            AllocationProgress refund
    ) {
        int quantity = maxImportableQuantity(item, receipt, refund);
        ResolvedAllocation allocation = resolveAllocation(item, quantity, receipt, refund);
        return new InvoiceSourceCandidateItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getMaterialId(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getWarehouseId(),
                item.getWarehouseName(),
                item.getBatchNo(),
                item.getBatchNoNormalized(),
                quantity,
                item.getQuantityUnit(),
                TradeItemCalculator.scaleWeightTon(item.getPieceWeightTon()),
                item.getPiecesPerBundle(),
                allocation.weightTon(),
                TradeItemCalculator.scaleAmount(item.getUnitPrice()),
                allocation.amount()
        );
    }

    private int maxImportableQuantity(
            PurchaseOrderItem item,
            AllocationProgress receipt,
            AllocationProgress refund
    ) {
        int sourceQuantity = item.getQuantity() == null ? 0 : item.getQuantity();
        long usedQuantity = Math.addExact(receipt.quantity(), refund.quantity());
        int quantityCapacity = (int) Math.max(0L, sourceQuantity - usedQuantity);
        BigDecimal sourceWeight = TradeItemCalculator.safeBigDecimal(item.getWeightTon());
        BigDecimal sourceAmount = TradeItemCalculator.safeBigDecimal(item.getAmount());
        int low = 0;
        int high = quantityCapacity;
        int result = 0;
        while (low <= high) {
            int candidate = low + (high - low) / 2;
            ResolvedAllocation allocation = resolveAllocation(item, candidate, receipt, refund);
            BigDecimal nextWeight = receipt.weightTon().add(refund.weightTon()).add(allocation.weightTon());
            BigDecimal nextAmount = receipt.amount().add(refund.amount()).add(allocation.amount());
            if (nextWeight.compareTo(sourceWeight) <= 0 && nextAmount.compareTo(sourceAmount) <= 0) {
                result = candidate;
                low = candidate + 1;
            } else {
                high = candidate - 1;
            }
        }
        return result;
    }

    private ResolvedAllocation resolveAllocation(
            PurchaseOrderItem item,
            int requestedQuantity,
            AllocationProgress receipt,
            AllocationProgress refund
    ) {
        long sourceQuantity = item.getQuantity() == null ? 0L : item.getQuantity().longValue();
        long remainingQuantity = Math.max(sourceQuantity - refund.quantity(), 0L);
        BigDecimal sourceWeight = TradeItemCalculator.calculateWeightTon(
                item.getQuantity(),
                item.getPieceWeightTon()
        );
        BigDecimal sourceAmount = TradeItemCalculator.calculateAmount(sourceWeight, item.getUnitPrice());
        BigDecimal remainingWeight = TradeItemCalculator.scaleWeightTon(
                sourceWeight.subtract(refund.weightTon()).max(BigDecimal.ZERO)
        );
        BigDecimal remainingAmount = TradeItemCalculator.scaleAmount(
                sourceAmount.subtract(refund.amount()).max(BigDecimal.ZERO)
        );
        if (requestedQuantity <= 0 || remainingQuantity <= 0) {
            return ResolvedAllocation.EMPTY;
        }
        boolean hasUnquantifiedReceiptValue = receipt.quantity() == 0L
                && (receipt.weightTon().compareTo(BigDecimal.ZERO) > 0
                || receipt.amount().compareTo(BigDecimal.ZERO) > 0);
        if (!hasUnquantifiedReceiptValue
                && Math.addExact(receipt.quantity(), requestedQuantity) == remainingQuantity) {
            return new ResolvedAllocation(
                    TradeItemCalculator.scaleWeightTon(
                            remainingWeight.subtract(receipt.weightTon()).max(BigDecimal.ZERO)
                    ),
                    TradeItemCalculator.scaleAmount(
                            remainingAmount.subtract(receipt.amount()).max(BigDecimal.ZERO)
                    )
            );
        }
        BigDecimal share = BigDecimal.valueOf(requestedQuantity);
        BigDecimal availableQuantity = BigDecimal.valueOf(remainingQuantity);
        return new ResolvedAllocation(
                remainingWeight.multiply(share).divide(
                        availableQuantity,
                        PrecisionConstants.WEIGHT_SCALE,
                        PrecisionConstants.DEFAULT_ROUNDING
                ),
                remainingAmount.multiply(share).divide(
                        availableQuantity,
                        PrecisionConstants.AMOUNT_SCALE,
                        PrecisionConstants.DEFAULT_ROUNDING
                )
        );
    }

    private Pageable sourceBatchPageable(PageQuery query, int pageIndex) {
        Sort sourceSort = query.toPageable("id").getSort();
        if (sourceSort.getOrderFor("id") == null) {
            Sort.Direction tieBreakerDirection = sourceSort.stream()
                    .findFirst()
                    .map(Sort.Order::getDirection)
                    .orElse(Sort.Direction.DESC);
            sourceSort = sourceSort.and(Sort.by(tieBreakerDirection, "id"));
        }
        return PageRequest.of(pageIndex, BATCH_SIZE, sourceSort);
    }

    private record ResolvedAllocation(BigDecimal weightTon, BigDecimal amount) {
        private static final ResolvedAllocation EMPTY = new ResolvedAllocation(
                TradeItemCalculator.scaleWeightTon(BigDecimal.ZERO),
                TradeItemCalculator.scaleAmount(BigDecimal.ZERO)
        );
    }
}
