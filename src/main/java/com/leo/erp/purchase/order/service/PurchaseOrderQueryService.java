package com.leo.erp.purchase.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderDerivedQueryRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderInboundCandidateQueryRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderImportCandidateResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderDerivedQueryRepository derivedQueryRepository;
    private final PurchaseOrderResponseAssembler responseAssembler;
    private final PurchaseOrderAvailabilityService availabilityService;
    private final PurchaseOrderInboundCandidateQueryRepository inboundCandidateQueryRepository;

    public PurchaseOrderQueryService(PurchaseOrderRepository purchaseOrderRepository,
                                     PurchaseOrderDerivedQueryRepository derivedQueryRepository,
                                     PurchaseOrderResponseAssembler responseAssembler,
                                     PurchaseOrderAvailabilityService availabilityService,
                                     PurchaseOrderInboundCandidateQueryRepository inboundCandidateQueryRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.derivedQueryRepository = derivedQueryRepository;
        this.responseAssembler = responseAssembler;
        this.availabilityService = availabilityService;
        this.inboundCandidateQueryRepository = inboundCandidateQueryRepository;
    }

    Page<PurchaseOrder> findPending(PageQuery query, PageFilter filter, String productKeyword) {
        return purchaseOrderRepository.findPending(
                normalizeContains(filter.keyword()),
                filter.supplierId(),
                normalizeExact(filter.name()),
                filter.settlementCompanyId(),
                normalizeExact(filter.status()),
                startDate(filter),
                endDateExclusive(filter),
                StatusConstants.PURCHASE_COMPLETED,
                normalizeContains(productKeyword),
                query.toPageable("id")
        );
    }

    /**
     * 列表汇总/默认分页路径的检索条件。
     *
     * <p>{@code productKeyword} 走明细集合的 EXISTS（{@link Specs#collectionKeywordLike}），
     * 与单据头 keyword 相互独立：前者搜商品（品牌/材质/规格/长度），后者搜单号/供应商。
     * 这条路径与 {@link #findPending} 的 JPQL 路径必须同时支持，
     * 否则会出现「默认列表搜得到、走待入库筛选就搜不到」这类不一致。</p>
     */
    Specification<PurchaseOrder> summarySpecification(PageFilter filter, String productKeyword) {
        return Specs.<PurchaseOrder>keywordLike(filter.keyword(), PURCHASE_ORDER_SEARCH_FIELDS)
                .and(Specs.collectionKeywordLike(productKeyword, "items", Specs.PRODUCT_SEARCH_FIELDS))
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.dateTimeBetweenDatesIfPresent("orderDate", filter.startDate(), filter.endDate()));
    }

    Map<Long, PurchaseOrderDerivedQueryRepository.DerivedStatus> findDerivedStatusByOrderIds(
            List<Long> orderIds) {
        return derivedQueryRepository == null
                ? Map.of()
                : derivedQueryRepository.findByOrderIds(orderIds);
    }

    /**
     * 应用派生聚合: 入库进度(未入库/已入库件数)、实际货值与差额。
     *
     * <p>这些列是派生值, 不随单据保存, 只在读取时补齐; 同一份聚合也只在这里落进响应。</p>
     */
    PurchaseOrderResponse applyDerivedValues(PurchaseOrderResponse response,
                                             PurchaseOrderDerivedQueryRepository.DerivedStatus status) {
        if (status == null) {
            return response;
        }
        return response
                .applyTotalRemainingQuantity(status.unreceivedQuantity())
                .applyDifference(status.actualAmount(), status.amountDifference())
                .applyReceivedQuantity(status.receivedQuantity());
    }

    PurchaseOrderResponse toDetailResponse(PurchaseOrder order) {
        PurchaseOrderResponse response = responseAssembler.toDetailResponse(order);
        PurchaseOrderDerivedQueryRepository.DerivedStatus status = derivedQueryRepository == null
                ? null
                : derivedQueryRepository.findByOrderIds(List.of(order.getId())).get(order.getId());
        return applyDerivedValues(response, status);
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
