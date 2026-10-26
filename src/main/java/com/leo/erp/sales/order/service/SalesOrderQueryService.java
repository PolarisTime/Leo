package com.leo.erp.sales.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderOutboundCandidateQueryRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class SalesOrderQueryService {

    private static final LocalDate MIN_PENDING_DELIVERY_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate MAX_PENDING_DELIVERY_DATE = LocalDate.of(9999, 12, 31);
    private static final String[] SALES_ORDER_SEARCH_FIELDS = {"orderNo", "purchaseOrderNo", "customerName", "projectName"};

    private final SalesOrderRepository repository;
    private final SalesOrderOutboundCandidateQueryRepository outboundCandidateQueryRepository;
    private final SalesOrderResponseAssembler responseAssembler;
    private final SalesOrderDerivedQuantityService derivedQuantityService;

    public SalesOrderQueryService(SalesOrderRepository repository,
                                  SalesOrderOutboundCandidateQueryRepository outboundCandidateQueryRepository,
                                  SalesOrderResponseAssembler responseAssembler,
                                  SalesOrderDerivedQuantityService derivedQuantityService) {
        this.repository = repository;
        this.outboundCandidateQueryRepository = outboundCandidateQueryRepository;
        this.responseAssembler = responseAssembler;
        this.derivedQuantityService = derivedQuantityService;
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> page(PageQuery query,
                                         PageFilter filter,
                                         String productKeyword,
                                         Boolean pendingOnly) {
        Page<SalesOrder> entities;
        LocalDate startDate = filter.startDate() == null
                ? MIN_PENDING_DELIVERY_DATE
                : filter.startDate();
        LocalDate endDate = filter.endDate() == null
                ? MAX_PENDING_DELIVERY_DATE
                : filter.endDate();
        // pendingOnly=true 等价于 findByReferenceFilter 的 pendingOnly 分支，
        // 原 findPending 查询已并入此处，保证同一 where 语义只维护一份。
        if (Boolean.TRUE.equals(pendingOnly)) {
            entities = repository.findByReferenceFilter(
                    normalizeContains(filter.keyword()),
                    filter.customerId(),
                    normalizeExact(filter.name()),
                    filter.projectId(),
                    normalizeExact(filter.projectName()),
                    filter.settlementCompanyId(),
                    normalizeContains(productKeyword),
                    normalizeExact(filter.status()),
                    startDate,
                    endDate,
                    StatusConstants.SALES_COMPLETED,
                    pendingOnly,
                    query.toPageable("id")
            );
        } else {
            Specification<SalesOrder> spec = Specs.<SalesOrder>keywordLike(filter.keyword(), SALES_ORDER_SEARCH_FIELDS)
                    // 商品字段清单的唯一来源：Specs.PRODUCT_SEARCH_FIELDS（含 length）。
                    .and(Specs.collectionKeywordLike(productKeyword, "items", Specs.PRODUCT_SEARCH_FIELDS))
                    .and(Specs.equalIfPresent("customerName", filter.name()))
                    .and(Specs.equalIfPresent("projectName", filter.projectName()))
                    .and(Specs.equalValueIfPresent("customerId", filter.customerId()))
                    .and(Specs.equalValueIfPresent("projectId", filter.projectId()))
                    .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                    .and(Specs.documentStatus(filter.status()))
                    .and(Specs.betweenIfPresent("deliveryDate", filter.startDate(), filter.endDate()));
            entities = repository.findAll(spec, query.toPageable("id"));
        }
        Map<Long, SalesOrderDerivedQuantityService.Quantities> quantities =
                derivedQuantityService.orderQuantities(
                        entities.getContent().stream().map(SalesOrder::getId).toList());
        return entities.map(order -> {
            SalesOrderResponse response = responseAssembler.toSummaryResponse(order);
            SalesOrderDerivedQuantityService.Quantities quantity = quantities
                    .getOrDefault(order.getId(), SalesOrderDerivedQuantityService.Quantities.ZERO);
            return response.withDerivedQuantities(
                    quantity.deliveredQuantity(),
                    quantity.returnedQuantity(),
                    quantity.deliveredNetQuantity());
        });
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<SalesOrderResponse> outboundImportCandidates(PageQuery query, PageFilter filter) {
        Page<Long> candidateIds = outboundCandidateQueryRepository.pageIds(query, filter);
        if (candidateIds.isEmpty()) {
            return new PageImpl<>(List.of(), candidateIds.getPageable(), candidateIds.getTotalElements());
        }
        List<SalesOrder> orders = repository.findByIdInAndDeletedFlagFalse(candidateIds.getContent());
        java.util.Map<Long, SalesOrder> orderById = orders.stream()
                .collect(java.util.stream.Collectors.toMap(SalesOrder::getId, order -> order));
        List<SalesOrder> ordered = candidateIds.getContent().stream()
                .map(orderById::get)
                .filter(Objects::nonNull)
                .toList();
        // 页级批量装配：本页全部明细的派生数量/占用与附加费用一次性聚合，避免逐单 N+1。
        List<SalesOrderResponse> candidates = responseAssembler.toDetailResponses(ordered);
        return new PageImpl<>(
                candidates,
                candidateIds.getPageable(),
                candidateIds.getTotalElements()
        );
    }

    public SalesOrderResponse toSummaryResponse(SalesOrder entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

    public SalesOrderResponse toDetailResponse(SalesOrder entity) {
        return responseAssembler.toDetailResponse(entity);
    }

    private static String normalizeContains(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeExact(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
