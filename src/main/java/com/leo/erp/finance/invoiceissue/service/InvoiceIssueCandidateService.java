package com.leo.erp.finance.invoiceissue.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.InvoiceAllocationSupport.AllocationProgress;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.common.web.dto.InvoiceSourceCandidateItemResponse;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueSourceCandidateResponse;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
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
public class InvoiceIssueCandidateService {

    private static final int BATCH_SIZE = 200;

    private final SalesOrderRepository salesOrderRepository;
    private final InvoiceIssueRepository invoiceIssueRepository;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;

    public InvoiceIssueCandidateService(SalesOrderRepository salesOrderRepository,
                                        InvoiceIssueRepository invoiceIssueRepository,
                                        ResourceRecordAccessGuard resourceRecordAccessGuard) {
        this.salesOrderRepository = salesOrderRepository;
        this.invoiceIssueRepository = invoiceIssueRepository;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceIssueSourceCandidateResponse> sourceCandidates(PageQuery query, PageFilter filter) {
        Specification<SalesOrder> specification = Specs.<SalesOrder>notDeleted()
                .and((root, criteriaQuery, criteriaBuilder) ->
                        root.get("status").in(StatusConstants.INVOICEABLE_SALES_ORDER_STATUS))
                .and(Specs.keywordLike(filter.keyword(), "orderNo", "customerName", "projectName"))
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(Specs.equalValueIfPresent("customerId", filter.customerId()))
                .and(Specs.equalValueIfPresent("projectId", filter.projectId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("deliveryDate", filter.startDate(), filter.endDate()));
        long requestedStart = (long) query.page() * query.size();
        long requestedEnd = requestedStart + query.size();
        long candidateCount = 0L;
        List<InvoiceIssueSourceCandidateResponse> requestedCandidates = new ArrayList<>(query.size());
        int pageIndex = 0;
        Page<SalesOrder> batch;
        do {
            batch = salesOrderRepository.findAll(
                    DataScopeContext.apply(specification),
                    sourceBatchPageable(query, pageIndex)
            );
            List<SalesOrder> orders = batch.getContent().stream()
                    .filter(order -> StatusConstants.INVOICEABLE_SALES_ORDER_STATUS.contains(order.getStatus()))
                    .toList();
            orders.forEach(order -> resourceRecordAccessGuard.assertCurrentUserCanAccess(
                    "sales-order",
                    "read",
                    order
            ));
            for (InvoiceIssueSourceCandidateResponse candidate : calculateCandidates(
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

    private List<InvoiceIssueSourceCandidateResponse> calculateCandidates(
            List<SalesOrder> orders,
            Long currentIssueId
    ) {
        List<Long> itemIds = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(SalesOrderItem::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (itemIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AllocationProgress> allocatedByItemId = new HashMap<>();
        invoiceIssueRepository.summarizeAllocatedBySourceSalesOrderItemIds(itemIds, currentIssueId)
                .forEach(summary -> allocatedByItemId.put(
                        summary.getSourceSalesOrderItemId(),
                        new AllocationProgress(
                                summary.getTotalQuantity() == null ? 0L : summary.getTotalQuantity(),
                                TradeItemCalculator.safeBigDecimal(summary.getTotalWeightTon()),
                                TradeItemCalculator.safeBigDecimal(summary.getTotalAmount())
                        )
                ));
        return orders.stream()
                .map(order -> toCandidate(order, allocatedByItemId))
                .filter(candidate -> !candidate.items().isEmpty())
                .toList();
    }

    private InvoiceIssueSourceCandidateResponse toCandidate(
            SalesOrder order,
            Map<Long, AllocationProgress> allocatedByItemId
    ) {
        List<InvoiceSourceCandidateItemResponse> items = order.getItems().stream()
                .filter(item -> item.getId() != null)
                .map(item -> toCandidateItem(
                        item,
                        allocatedByItemId.getOrDefault(item.getId(), AllocationProgress.EMPTY)
                ))
                .filter(item -> item.quantity() > 0)
                .toList();
        BigDecimal totalWeight = items.stream()
                .map(InvoiceSourceCandidateItemResponse::weightTon)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = items.stream()
                .map(InvoiceSourceCandidateItemResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InvoiceIssueSourceCandidateResponse(
                order.getId(),
                order.getOrderNo(),
                order.getCustomerCode(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getProjectId(),
                order.getProjectName(),
                order.getSettlementCompanyId(),
                order.getSettlementCompanyName(),
                order.getDeliveryDate(),
                TradeItemCalculator.scaleWeightTon(totalWeight),
                TradeItemCalculator.scaleAmount(totalAmount),
                order.getStatus(),
                items
        );
    }

    private InvoiceSourceCandidateItemResponse toCandidateItem(
            SalesOrderItem item,
            AllocationProgress allocated
    ) {
        int quantity = maxImportableQuantity(item, allocated);
        BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(quantity, item.getPieceWeightTon());
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, item.getUnitPrice());
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
                weightTon,
                TradeItemCalculator.scaleAmount(item.getUnitPrice()),
                amount
        );
    }

    private int maxImportableQuantity(SalesOrderItem item, AllocationProgress allocated) {
        int sourceQuantity = item.getQuantity() == null ? 0 : item.getQuantity();
        int quantityCapacity = (int) Math.max(0L, sourceQuantity - allocated.quantity());
        BigDecimal weightCapacity = TradeItemCalculator.safeBigDecimal(item.getWeightTon())
                .subtract(allocated.weightTon())
                .max(BigDecimal.ZERO);
        BigDecimal amountCapacity = TradeItemCalculator.safeBigDecimal(item.getAmount())
                .subtract(allocated.amount())
                .max(BigDecimal.ZERO);
        int low = 0;
        int high = quantityCapacity;
        int result = 0;
        while (low <= high) {
            int candidate = low + (high - low) / 2;
            BigDecimal weight = TradeItemCalculator.calculateWeightTon(candidate, item.getPieceWeightTon());
            BigDecimal amount = TradeItemCalculator.calculateAmount(weight, item.getUnitPrice());
            if (weight.compareTo(weightCapacity) <= 0 && amount.compareTo(amountCapacity) <= 0) {
                result = candidate;
                low = candidate + 1;
            } else {
                high = candidate - 1;
            }
        }
        return result;
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
}
