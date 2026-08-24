package com.leo.erp.sales.order.service;

import com.leo.erp.common.api.CandidatePageables;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SalesOrderStatementSourceQueryService implements SalesOrderStatementSourceQuery {

    private static final String[] CANDIDATE_SEARCH_FIELDS = {
            "orderNo",
            "purchaseInboundNo",
            "purchaseOrderNo",
            "customerName",
            "projectName",
            "salesName"
    };

    private final SalesOrderRepository salesOrderRepository;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "orderNo", "purchaseInboundNo", "purchaseOrderNo", "customerName", "projectName", "deliveryDate", "salesName", "totalWeight", "totalAmount", "status");
    private final SalesOutboundRepository salesOutboundRepository;

    public SalesOrderStatementSourceQueryService(SalesOrderRepository salesOrderRepository,
                                                 SalesOutboundRepository salesOutboundRepository) {
        this.salesOrderRepository = salesOrderRepository;
        this.salesOutboundRepository = salesOutboundRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CandidatePage findCandidates(CandidateCriteria criteria) {
        Set<Long> excludedOrderIds = criteria.excludedSourceItemIds().isEmpty()
                ? Set.of()
                : salesOrderRepository.findAllWithItemsBySourceItemIds(criteria.excludedSourceItemIds()).stream()
                        .map(SalesOrder::getId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Specification<SalesOrder> specification = Specs.<SalesOrder>notDeleted()
                .and(Specs.keywordLike(criteria.keyword(), CANDIDATE_SEARCH_FIELDS))
                .and(Specs.equalValueIfPresent("customerId", criteria.customerId()))
                .and(Specs.equalValueIfPresent("projectId", criteria.projectId()))
                .and(Specs.equalIfPresent("customerName", criteria.customerName()))
                .and(Specs.equalIfPresent("projectName", criteria.projectName()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", criteria.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", StatusConstants.SALES_COMPLETED))
                .and(Specs.betweenIfPresent("deliveryDate", criteria.startDate(), criteria.endDate()))
                .and(Specs.idNotIn(excludedOrderIds));
        Page<CandidateSnapshot> page = salesOrderRepository.findAll(specification, CandidatePageables.of(
                criteria.page(), criteria.size(), criteria.sortBy(), criteria.direction(), ALLOWED_SORT_FIELDS))
                .map(this::toCandidateSnapshot);
        return new CandidatePage(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSnapshot> findBySourceItemIds(Collection<Long> sourceItemIds) {
        Set<Long> requestedItemIds = sourceItemIds == null
                ? Set.of()
                : sourceItemIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requestedItemIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AuditedOutboundActualSnapshot> outboundActuals = loadAuditedOutboundActuals(requestedItemIds);
        return salesOrderRepository.findAllWithItemsBySourceItemIds(requestedItemIds).stream()
                .map(order -> toOrderSnapshot(order, outboundActuals))
                .toList();
    }

    private Map<Long, AuditedOutboundActualSnapshot> loadAuditedOutboundActuals(Set<Long> sourceItemIds) {
        Map<Long, AuditedOutboundActualSnapshot> result = new HashMap<>();
        salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                        StatusConstants.AUDITED,
                        sourceItemIds.stream().sorted().toList()
                ).stream()
                .flatMap(outbound -> outbound.getItems().stream())
                .filter(item -> item.getSourceSalesOrderItemId() != null)
                .filter(item -> sourceItemIds.contains(item.getSourceSalesOrderItemId()))
                .forEach(item -> result.merge(
                        item.getSourceSalesOrderItemId(),
                        toActualSnapshot(item),
                        this::mergeActuals
                ));
        return Map.copyOf(result);
    }

    private AuditedOutboundActualSnapshot toActualSnapshot(SalesOutboundItem item) {
        return new AuditedOutboundActualSnapshot(
                item.getQuantity() == null ? 0L : item.getQuantity().longValue(),
                TradeItemCalculator.scaleWeightTon(item.getWeightTon()),
                TradeItemCalculator.scaleAmount(item.getAmount())
        );
    }

    private AuditedOutboundActualSnapshot mergeActuals(AuditedOutboundActualSnapshot left,
                                                       AuditedOutboundActualSnapshot right) {
        return new AuditedOutboundActualSnapshot(
                Math.addExact(left.quantity(), right.quantity()),
                TradeItemCalculator.scaleWeightTon(left.weightTon().add(right.weightTon())),
                TradeItemCalculator.scaleAmount(left.amount().add(right.amount()))
        );
    }

    private OrderSnapshot toOrderSnapshot(SalesOrder order,
                                          Map<Long, AuditedOutboundActualSnapshot> outboundActuals) {
        return new OrderSnapshot(
                order.getId(),
                order.getOrderNo(),
                order.getCustomerCode(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getProjectId(),
                order.getProjectName(),
                order.getSettlementCompanyId(),
                order.getSettlementCompanyName(),
                order.getStatus(),
                order.getItems().stream()
                        .map(item -> toItemSnapshot(item, outboundActuals.get(item.getId())))
                        .toList()
        );
    }

    private ItemSnapshot toItemSnapshot(SalesOrderItem item, AuditedOutboundActualSnapshot outboundActual) {
        return new ItemSnapshot(
                item.getId(),
                item.getMaterialId(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getWarehouseId(),
                item.getBatchNo(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                item.getUnitPrice(),
                item.getAmount(),
                outboundActual
        );
    }

    private CandidateSnapshot toCandidateSnapshot(SalesOrder order) {
        return new CandidateSnapshot(
                order.getId(),
                order.getOrderNo(),
                order.getCustomerName(),
                order.getProjectName(),
                order.getSettlementCompanyId(),
                order.getSettlementCompanyName(),
                order.getDeliveryDate(),
                order.getSalesName(),
                order.getTotalWeight(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCustomerId(),
                order.getProjectId()
        );
    }

}
