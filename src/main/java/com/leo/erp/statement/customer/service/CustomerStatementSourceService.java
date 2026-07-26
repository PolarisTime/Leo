package com.leo.erp.statement.customer.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.AuditedOutboundActualSnapshot;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.CandidateCriteria;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.CandidateSnapshot;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.ItemSnapshot;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.OrderSnapshot;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.service.StatementSourceCoverageValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

@Service
public class CustomerStatementSourceService {

    private final CustomerStatementRepository repository;
    private final SalesOrderStatementSourceQuery sourceQuery;
    private final CustomerRepository customerRepository;

    public CustomerStatementSourceService(CustomerStatementRepository repository,
                                          SalesOrderStatementSourceQuery sourceQuery,
                                          CustomerRepository customerRepository) {
        this.repository = repository;
        this.sourceQuery = sourceQuery;
        this.customerRepository = customerRepository;
    }

    Page<CustomerStatementCandidateResponse> candidatePage(PageQuery query, PageFilter filter) {
        List<Long> occupiedSourceItemIds = repository
                .findOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(filter.currentRecordId());
        SalesOrderStatementSourceQuery.CandidatePage page = sourceQuery.findCandidates(new CandidateCriteria(
                query.page(),
                query.size(),
                query.sortBy(),
                query.direction(),
                filter.keyword(),
                filter.customerId(),
                filter.projectId(),
                filter.name(),
                filter.projectName(),
                filter.settlementCompanyId(),
                filter.startDate(),
                filter.endDate(),
                occupiedSourceItemIds
        ));
        return new PageImpl<>(
                page.content().stream().map(this::toCandidateResponse).toList(),
                query.toPageable("id"),
                page.totalElements()
        );
    }

    SourceApplyResult applyItems(CustomerStatement entity,
                                 CustomerStatementRequest request,
                                 LongSupplier nextIdSupplier) {
        BigDecimal salesAmount = BigDecimal.ZERO;
        Map<Long, SourceSalesOrderItem> sourceSalesOrderItemMap = loadSourceSalesOrderItemMap(request.items());
        validateSourceSalesOrders(request, sourceSalesOrderItemMap, entity.getId());
        List<OrderSnapshot> sourceOrders = sourceSalesOrderItemMap.values().stream()
                .map(SourceSalesOrderItem::order)
                .distinct()
                .toList();
        SettlementCompanySnapshot settlementCompany = resolveStatementSettlementCompany(sourceOrders);
        PartyIdentity partyIdentity = resolvePartyIdentity(sourceSalesOrderItemMap.values());
        entity.setCustomerId(partyIdentity.customerId());
        entity.setProjectId(partyIdentity.projectId());
        entity.setCustomerCode(resolveCustomerCode(
                request.customerCode(),
                request.customerName(),
                request.projectName(),
                sourceSalesOrderItemMap
        ));
        List<CustomerStatementItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                CustomerStatementItem::getId,
                CustomerStatementItemRequest::id,
                CustomerStatementItem::new,
                nextIdSupplier,
                CustomerStatementItem::setId
        );
        for (int i = 0; i < request.items().size(); i++) {
            CustomerStatementItemRequest source = request.items().get(i);
            SourceSalesOrderItem sourceSalesOrderItem = resolveSourceSalesOrderItem(
                    source,
                    sourceSalesOrderItemMap,
                    i + 1
            );
            OrderSnapshot sourceOrder = sourceSalesOrderItem.order();
            ItemSnapshot sourceItem = sourceSalesOrderItem.item();
            AuditedOutboundActualSnapshot outboundActual = resolveAuditedOutboundActual(sourceItem, i + 1);
            CustomerStatementItem item = items.get(i);
            item.setCustomerStatement(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(sourceOrder.orderNo());
            item.setSourceSalesOrderItemId(sourceItem.id());
            item.setCustomerId(sourceOrder.customerId());
            item.setProjectId(sourceOrder.projectId());
            item.setMaterialId(sourceItem.materialId());
            item.setWarehouseId(sourceItem.warehouseId());
            item.setMaterialCode(sourceItem.materialCode());
            item.setBrand(sourceItem.brand());
            item.setCategory(sourceItem.category());
            item.setMaterial(sourceItem.material());
            item.setSpec(sourceItem.spec());
            item.setLength(sourceItem.length());
            item.setUnit(sourceItem.unit());
            item.setBatchNo(sourceItem.batchNo());
            item.setQuantity(Math.toIntExact(outboundActual.quantity()));
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(sourceItem.quantityUnit()));
            item.setPieceWeightTon(TradeItemCalculator.scaleWeightTon(sourceItem.pieceWeightTon()));
            item.setPiecesPerBundle(sourceItem.piecesPerBundle());
            item.setWeightTon(outboundActual.weightTon());
            item.setUnitPrice(TradeItemCalculator.scaleAmount(sourceItem.unitPrice()));
            BigDecimal amount = outboundActual.amount();
            item.setAmount(amount);
            salesAmount = salesAmount.add(amount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(CustomerStatementItem::getLineNo));
        return new SourceApplyResult(salesAmount, settlementCompany.id(), settlementCompany.name());
    }

    private Map<Long, SourceSalesOrderItem> loadSourceSalesOrderItemMap(List<CustomerStatementItemRequest> items) {
        Set<Long> uniqueSourceItemIds = new LinkedHashSet<>();
        for (CustomerStatementItemRequest item : items) {
            Long sourceItemId = item.sourceSalesOrderItemId();
            if (sourceItemId != null && !uniqueSourceItemIds.add(sourceItemId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "来源销售订单明细ID重复");
            }
        }
        List<Long> sourceSalesOrderItemIds = List.copyOf(uniqueSourceItemIds);
        if (sourceSalesOrderItemIds.isEmpty()) {
            return Map.of();
        }
        return sourceQuery.findBySourceItemIds(sourceSalesOrderItemIds).stream()
                .flatMap(order -> order.items().stream()
                        .filter(item -> uniqueSourceItemIds.contains(item.id()))
                        .map(item -> new SourceSalesOrderItem(order, item)))
                .collect(java.util.stream.Collectors.toMap(source -> source.item().id(), source -> source));
    }

    private AuditedOutboundActualSnapshot resolveAuditedOutboundActual(ItemSnapshot sourceItem, int lineNo) {
        AuditedOutboundActualSnapshot actual = sourceItem.auditedOutboundActual();
        if (actual == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行来源销售订单明细没有已审核销售出库，不能生成客户对账单"
            );
        }
        return actual;
    }

    private void validateSourceSalesOrders(CustomerStatementRequest request,
                                           Map<Long, SourceSalesOrderItem> sourceSalesOrderItemMap,
                                           Long currentStatementId) {
        Map<Long, OrderSnapshot> requestedOrders = new java.util.LinkedHashMap<>();
        for (SourceSalesOrderItem item : sourceSalesOrderItemMap.values()) {
            OrderSnapshot order = item.order();
            requestedOrders.put(order.id(), order);
            BusinessDocumentValidator.requireSameText(
                    request.customerName(),
                    order.customerName(),
                    "来源销售订单存在不同客户，不能合并生成客户对账单"
            );
            BusinessDocumentValidator.requireSameText(
                    request.projectName(),
                    order.projectName(),
                    "来源销售订单存在不同项目，不能合并生成客户对账单"
            );
            BusinessDocumentValidator.requireStatusIn(
                    order.status(),
                    Set.of(StatusConstants.SALES_COMPLETED),
                    "来源销售订单" + order.orderNo() + "未完成销售，不能生成客户对账单"
            );
            if (request.settlementCompanyId() != null
                    && order.settlementCompanyId() != null
                    && !request.settlementCompanyId().equals(order.settlementCompanyId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售订单存在不同客户结算主体，不能合并生成客户对账单");
            }
            requireSameIdentity(request.customerId(), order.customerId(), "客户ID与来源销售订单不一致");
            requireSameIdentity(request.projectId(), order.projectId(), "项目ID与来源销售订单不一致");
        }
        if (requestedOrders.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户对账单来源销售订单不能为空");
        }
        assertCompleteSourceItemCoverage(sourceSalesOrderItemMap.values());
        assertSourceOrdersNotOccupied(requestedOrders, currentStatementId);
    }

    private void assertCompleteSourceItemCoverage(Collection<SourceSalesOrderItem> requestedItems) {
        requestedItems.stream()
                .map(SourceSalesOrderItem::order)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(order -> StatementSourceCoverageValidator.requireAllEffectiveItems(
                        "来源销售订单" + order.orderNo(),
                        order.items().stream().map(ItemSnapshot::id).toList(),
                        requestedItems.stream()
                                .filter(item -> sameSalesOrder(item.order(), order))
                                .map(item -> item.item().id())
                                .toList()
                ));
    }

    private boolean sameSalesOrder(OrderSnapshot left, OrderSnapshot right) {
        if (left == right) {
            return true;
        }
        return left != null
                && right != null
                && left.id() != null
                && left.id().equals(right.id());
    }

    private void assertSourceOrdersNotOccupied(Map<Long, OrderSnapshot> requestedOrders,
                                               Long currentStatementId) {
        List<Long> requestedItemIds = requestedOrders.values().stream()
                .flatMap(order -> order.items().stream())
                .map(ItemSnapshot::id)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        Set<Long> occupiedItemIds = requestedItemIds.isEmpty()
                ? Set.of()
                : toIdSet(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(
                        requestedItemIds,
                        currentStatementId
                ));
        for (OrderSnapshot order : requestedOrders.values()) {
            boolean occupied = order.items().stream()
                    .map(ItemSnapshot::id)
                    .anyMatch(occupiedItemIds::contains);
            if (occupied) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "来源销售订单" + order.orderNo() + "已生成客户对账单"
                );
            }
        }
    }

    private SourceSalesOrderItem resolveSourceSalesOrderItem(CustomerStatementItemRequest source,
                                                             Map<Long, SourceSalesOrderItem> sourceSalesOrderItemMap,
                                                             int lineNo) {
        Long sourceSalesOrderItemId = source.sourceSalesOrderItemId();
        if (sourceSalesOrderItemId != null) {
            SourceSalesOrderItem sourceSalesOrderItem = sourceSalesOrderItemMap.get(sourceSalesOrderItemId);
            if (sourceSalesOrderItem == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不存在");
            }
            requireSameIdentity(source.customerId(), sourceSalesOrderItem.order().customerId(),
                    "第" + lineNo + "行客户ID与来源销售订单不一致");
            requireSameIdentity(source.projectId(), sourceSalesOrderItem.order().projectId(),
                    "第" + lineNo + "行项目ID与来源销售订单不一致");
            requireSameIdentity(source.materialId(), sourceSalesOrderItem.item().materialId(),
                    "第" + lineNo + "行商品ID与来源销售订单不一致");
            requireSameIdentity(source.warehouseId(), sourceSalesOrderItem.item().warehouseId(),
                    "第" + lineNo + "行仓库ID与来源销售订单不一致");
            return sourceSalesOrderItem;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不能为空");
    }

    private String resolveCustomerCode(String requestCustomerCode,
                                       String customerName,
                                       String projectName,
                                       Map<Long, SourceSalesOrderItem> sourceSalesOrderItemMap) {
        String resolvedCode = trimToNull(requestCustomerCode);
        for (SourceSalesOrderItem item : sourceSalesOrderItemMap.values()) {
            resolvedCode = mergeCustomerCode(resolvedCode, trimToNull(item.order().customerCode()));
        }
        if (resolvedCode != null || customerRepository == null) {
            return resolvedCode;
        }
        String normalizedCustomerName = trimToNull(customerName);
        String normalizedProjectName = trimToNull(projectName);
        if (normalizedCustomerName == null || normalizedProjectName == null) {
            return null;
        }
        return customerRepository.findFirstByCustomerNameAndProjectNameAndDeletedFlagFalseOrderByCustomerCodeAsc(
                        normalizedCustomerName,
                        normalizedProjectName
                )
                .map(com.leo.erp.master.customer.domain.entity.Customer::getCustomerCode)
                .orElse(null);
    }

    private PartyIdentity resolvePartyIdentity(Collection<SourceSalesOrderItem> sourceItems) {
        List<PartyIdentity> identities = sourceItems.stream()
                .map(SourceSalesOrderItem::order)
                .filter(java.util.Objects::nonNull)
                .map(order -> new PartyIdentity(order.customerId(), order.projectId()))
                .distinct()
                .toList();
        if (identities.size() != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "来源销售订单存在不同客户或项目ID，不能合并生成客户对账单");
        }
        return identities.get(0);
    }

    private void requireSameIdentity(Long requestedId, Long sourceId, String message) {
        if (requestedId != null && !requestedId.equals(sourceId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    private String mergeCustomerCode(String currentCode, String nextCode) {
        if (currentCode == null) {
            return nextCode;
        }
        if (nextCode == null || currentCode.equals(nextCode)) {
            return currentCode;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售订单客户编码与客户对账单客户编码不一致");
    }

    private String trimToNull(String value) {
        return BusinessDocumentValidator.trimToNull(value);
    }

    private Set<Long> toIdSet(Collection<Long> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }

    private SettlementCompanySnapshot resolveStatementSettlementCompany(List<OrderSnapshot> orders) {
        List<SettlementCompanySnapshot> snapshots = orders.stream()
                .map(order -> new SettlementCompanySnapshot(
                        order.settlementCompanyId(),
                        trimToNull(order.settlementCompanyName())
                ))
                .filter(snapshot -> snapshot.id() != null || snapshot.name() != null)
                .distinct()
                .toList();
        if (snapshots.isEmpty()) {
            return new SettlementCompanySnapshot(null, null);
        }
        if (snapshots.size() > 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售订单存在不同客户结算主体，不能合并生成客户对账单");
        }
        return snapshots.get(0);
    }

    private CustomerStatementCandidateResponse toCandidateResponse(CandidateSnapshot order) {
        return new CustomerStatementCandidateResponse(
                order.id(),
                order.orderNo(),
                order.customerName(),
                order.projectName(),
                order.settlementCompanyId(),
                order.settlementCompanyName(),
                order.deliveryDate(),
                order.salesName(),
                order.totalWeight(),
                order.totalAmount(),
                order.status(),
                order.customerId(),
                order.projectId()
        );
    }

    record SourceApplyResult(
            BigDecimal salesAmount,
            Long settlementCompanyId,
            String settlementCompanyName
    ) {
    }

    private record SettlementCompanySnapshot(Long id, String name) {
    }

    private record PartyIdentity(Long customerId, Long projectId) {
    }

    private record SourceSalesOrderItem(OrderSnapshot order, ItemSnapshot item) {
    }
}
