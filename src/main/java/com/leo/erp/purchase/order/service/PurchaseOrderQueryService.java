package com.leo.erp.purchase.order.service;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderInboundCandidateQueryRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderReferenceQueryRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderImportCandidateResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderQueryService {

    static final String[] PURCHASE_ORDER_SEARCH_FIELDS = {"orderNo", "supplierName"};

    private static final LocalDateTime MIN_PENDING_ORDER_DATE = LocalDateTime.of(1, 1, 1, 0, 0);
    private static final LocalDateTime MAX_PENDING_ORDER_DATE_EXCLUSIVE = LocalDateTime.of(10000, 1, 1, 0, 0);
    private static final Set<String> REFERENCED_BY_VALUES =
            Set.of(
                    ModuleKeys.SALES_ORDER,
                    ModuleKeys.PURCHASE_INBOUND,
                    "none"
            );

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderReferenceQueryRepository referenceQueryRepository;
    private final PurchaseOrderResponseAssembler responseAssembler;
    private final PurchaseOrderAvailabilityService availabilityService;
    private final PurchaseOrderInboundCandidateQueryRepository inboundCandidateQueryRepository;

    public PurchaseOrderQueryService(PurchaseOrderRepository purchaseOrderRepository,
                                     PurchaseOrderReferenceQueryRepository referenceQueryRepository,
                                     PurchaseOrderResponseAssembler responseAssembler,
                                     PurchaseOrderAvailabilityService availabilityService,
                                     PurchaseOrderInboundCandidateQueryRepository inboundCandidateQueryRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.referenceQueryRepository = referenceQueryRepository;
        this.responseAssembler = responseAssembler;
        this.availabilityService = availabilityService;
        this.inboundCandidateQueryRepository = inboundCandidateQueryRepository;
    }

    Page<PurchaseOrder> findByReferenceFilter(PageQuery query,
                                              PageFilter filter,
                                              Boolean pendingOnly,
                                              Boolean referenced,
                                              String referencedBy) {
        return purchaseOrderRepository.findByReferenceFilter(
                normalizeContains(filter.keyword()),
                filter.supplierId(),
                normalizeExact(filter.name()),
                filter.settlementCompanyId(),
                normalizeExact(filter.status()),
                startDate(filter),
                endDateExclusive(filter),
                StatusConstants.PURCHASE_COMPLETED,
                pendingOnly,
                referenced,
                validateReferencedBy(referencedBy),
                query.toPageable("id")
        );
    }

    Page<PurchaseOrder> findPending(PageQuery query, PageFilter filter) {
        return purchaseOrderRepository.findPending(
                normalizeContains(filter.keyword()),
                filter.supplierId(),
                normalizeExact(filter.name()),
                filter.settlementCompanyId(),
                normalizeExact(filter.status()),
                startDate(filter),
                endDateExclusive(filter),
                StatusConstants.PURCHASE_COMPLETED,
                query.toPageable("id")
        );
    }

    Specification<PurchaseOrder> summarySpecification(PageFilter filter) {
        return Specs.<PurchaseOrder>keywordLike(filter.keyword(), PURCHASE_ORDER_SEARCH_FIELDS)
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.dateTimeBetweenDatesIfPresent("orderDate", filter.startDate(), filter.endDate()));
    }

    Map<Long, PurchaseOrderReferenceQueryRepository.ReferenceStatus> findReferenceStatusByOrderIds(
            List<Long> orderIds) {
        return referenceQueryRepository == null
                ? Map.of()
                : referenceQueryRepository.findByOrderIds(orderIds);
    }

    PurchaseOrderResponse applyReferenceFlags(PurchaseOrderResponse response,
                                              PurchaseOrderReferenceQueryRepository.ReferenceStatus status) {
        return status == null
                ? response
                : response.withReferenceFlags(
                        status.referencedBySalesOrder(),
                        status.referencedByPurchaseInbound()
                );
    }

    PurchaseOrderResponse toDetailResponse(PurchaseOrder order) {
        PurchaseOrderResponse response = responseAssembler.toDetailResponse(order);
        PurchaseOrderReferenceQueryRepository.ReferenceStatus status = referenceQueryRepository == null
                ? null
                : referenceQueryRepository.findByOrderIds(List.of(order.getId())).get(order.getId());
        return applyReferenceFlags(response, status);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<PurchaseOrderImportCandidateResponse> inboundImportCandidates(PageQuery query, PageFilter filter) {
        Page<Long> candidateIds = inboundCandidateQueryRepository.pageIds(query, filter);
        List<PurchaseOrder> orders = candidateIds.isEmpty()
                ? List.of()
                : purchaseOrderRepository.findByIdInAndDeletedFlagFalse(candidateIds.getContent());
        Map<Long, PurchaseOrder> orderById = orders.stream()
                .collect(Collectors.toMap(PurchaseOrder::getId, Function.identity()));
        List<PurchaseOrder> orderedOrders = candidateIds.getContent().stream()
                .map(orderById::get)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Integer> importableQuantityMap =
                availabilityService.buildInboundImportableQuantityMap(
                        orderedOrders,
                        filter.currentRecordId()
                );
        List<PurchaseOrderImportCandidateResponse> candidates = orderedOrders.stream()
                .map(order -> toImportCandidateResponse(
                        order,
                        importableQuantityMap.getOrDefault(order.getId(), 0)
                ))
                .filter(candidate -> candidate.importableQuantity() > 0)
                .toList();
        return new PageImpl<>(
                candidates,
                candidateIds.getPageable(),
                candidateIds.getTotalElements()
        );
    }

    private static LocalDateTime startDate(PageFilter filter) {
        return filter.startDate() == null
                ? MIN_PENDING_ORDER_DATE
                : filter.startDate().atStartOfDay();
    }

    private static LocalDateTime endDateExclusive(PageFilter filter) {
        return filter.endDate() == null
                ? MAX_PENDING_ORDER_DATE_EXCLUSIVE
                : filter.endDate().plusDays(1).atStartOfDay();
    }

    /** 校验下游模块引用筛选取值，非法取值直接拒绝请求。 */
    private static String validateReferencedBy(String referencedBy) {
        if (referencedBy == null || REFERENCED_BY_VALUES.contains(referencedBy)) {
            return referencedBy;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的下游模块关联筛选值: " + referencedBy);
    }

    private static String normalizeContains(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeExact(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PurchaseOrderImportCandidateResponse toImportCandidateResponse(PurchaseOrder order, Integer importableQuantity) {
        return new PurchaseOrderImportCandidateResponse(
                order.getId(),
                order.getOrderNo(),
                order.getSupplierId(),
                order.getSupplierCode(),
                order.getSupplierName(),
                order.getSettlementCompanyId(),
                order.getSettlementCompanyName(),
                order.getBuyerName(),
                order.getOrderDate(),
                order.getTotalWeight(),
                order.getTotalAmount(),
                order.getStatus(),
                importableQuantity
        );
    }
}
