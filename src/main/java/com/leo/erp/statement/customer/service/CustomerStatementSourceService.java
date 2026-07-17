package com.leo.erp.statement.customer.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.service.StatementCandidateSupport;
import com.leo.erp.statement.service.StatementSourceCoverageValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final String[] SALES_ORDER_CANDIDATE_SEARCH_FIELDS = {
            "orderNo",
            "purchaseInboundNo",
            "purchaseOrderNo",
            "customerName",
            "projectName",
            "salesName"
    };

    private final CustomerStatementRepository repository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemQueryService salesOrderItemQueryService;
    private final CustomerRepository customerRepository;
    private final SalesOutboundRepository salesOutboundRepository;

    public CustomerStatementSourceService(CustomerStatementRepository repository,
                                          SalesOrderRepository salesOrderRepository,
                                          SalesOrderItemQueryService salesOrderItemQueryService,
                                          CustomerRepository customerRepository) {
        this(repository, salesOrderRepository, salesOrderItemQueryService, customerRepository, null);
    }

    @Autowired
    public CustomerStatementSourceService(CustomerStatementRepository repository,
                                          SalesOrderRepository salesOrderRepository,
                                          SalesOrderItemQueryService salesOrderItemQueryService,
                                          CustomerRepository customerRepository,
                                          SalesOutboundRepository salesOutboundRepository) {
        this.repository = repository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderItemQueryService = salesOrderItemQueryService;
        this.customerRepository = customerRepository;
        this.salesOutboundRepository = salesOutboundRepository;
    }

    Page<CustomerStatementCandidateResponse> candidatePage(PageQuery query, PageFilter filter) {
        Set<Long> occupiedOrderIds = toIdSet(
                repository.findOccupiedSourceSalesOrderIdsExcludingCurrentStatement(filter.currentRecordId())
        );
        Specification<SalesOrder> spec = Specs.<SalesOrder>notDeleted()
                .and(Specs.keywordLike(filter.keyword(), SALES_ORDER_CANDIDATE_SEARCH_FIELDS))
                .and(Specs.equalValueIfPresent("customerId", filter.customerId()))
                .and(Specs.equalValueIfPresent("projectId", filter.projectId()))
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", StatusConstants.SALES_COMPLETED))
                .and(Specs.betweenIfPresent("deliveryDate", filter.startDate(), filter.endDate()))
                .and(StatementCandidateSupport.excludeFieldValues("id", occupiedOrderIds));
        return salesOrderRepository.findAll(spec, query.toPageable("id"))
                .map(this::toCandidateResponse);
    }

    SourceApplyResult applyItems(CustomerStatement entity,
                                 CustomerStatementRequest request,
                                 LongSupplier nextIdSupplier) {
        BigDecimal salesAmount = BigDecimal.ZERO;
        Map<Long, SalesOrderItem> sourceSalesOrderItemMap = loadSourceSalesOrderItemMap(request.items());
        validateSourceSalesOrders(request, sourceSalesOrderItemMap, entity.getId());
        Map<Long, AuditedOutboundActual> outboundActualMap = loadAuditedOutboundActualMap(
                sourceSalesOrderItemMap.keySet()
        );
        SettlementCompanySnapshot settlementCompany = resolveStatementSettlementCompany(sourceSalesOrderItemMap.values().stream()
                .map(SalesOrderItem::getSalesOrder)
                .distinct()
                .toList());
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
            SalesOrderItem sourceSalesOrderItem = resolveSourceSalesOrderItem(source, sourceSalesOrderItemMap, i + 1);
            AuditedOutboundActual outboundActual = resolveAuditedOutboundActual(
                    sourceSalesOrderItem,
                    outboundActualMap,
                    i + 1
            );
            CustomerStatementItem item = items.get(i);
            item.setCustomerStatement(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(sourceSalesOrderItem.getSalesOrder().getOrderNo());
            item.setSourceSalesOrderItemId(sourceSalesOrderItem.getId());
            item.setCustomerId(sourceSalesOrderItem.getSalesOrder().getCustomerId());
            item.setProjectId(sourceSalesOrderItem.getSalesOrder().getProjectId());
            item.setMaterialId(sourceSalesOrderItem.getMaterialId());
            item.setWarehouseId(sourceSalesOrderItem.getWarehouseId());
            item.setMaterialCode(sourceSalesOrderItem.getMaterialCode());
            item.setBrand(sourceSalesOrderItem.getBrand());
            item.setCategory(sourceSalesOrderItem.getCategory());
            item.setMaterial(sourceSalesOrderItem.getMaterial());
            item.setSpec(sourceSalesOrderItem.getSpec());
            item.setLength(sourceSalesOrderItem.getLength());
            item.setUnit(sourceSalesOrderItem.getUnit());
            item.setBatchNo(sourceSalesOrderItem.getBatchNo());
            item.setQuantity(outboundActual == null
                    ? sourceSalesOrderItem.getQuantity()
                    : Math.toIntExact(outboundActual.quantity()));
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(sourceSalesOrderItem.getQuantityUnit()));
            item.setPieceWeightTon(TradeItemCalculator.scaleWeightTon(sourceSalesOrderItem.getPieceWeightTon()));
            item.setPiecesPerBundle(sourceSalesOrderItem.getPiecesPerBundle());
            item.setWeightTon(outboundActual == null
                    ? TradeItemCalculator.scaleWeightTon(sourceSalesOrderItem.getWeightTon())
                    : outboundActual.weightTon());
            item.setUnitPrice(TradeItemCalculator.scaleAmount(sourceSalesOrderItem.getUnitPrice()));
            BigDecimal amount = outboundActual == null
                    ? TradeItemCalculator.scaleAmount(sourceSalesOrderItem.getAmount())
                    : outboundActual.amount();
            item.setAmount(amount);
            salesAmount = salesAmount.add(amount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(CustomerStatementItem::getLineNo));
        return new SourceApplyResult(salesAmount, settlementCompany.id(), settlementCompany.name());
    }

    private Map<Long, SalesOrderItem> loadSourceSalesOrderItemMap(List<CustomerStatementItemRequest> items) {
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
        return salesOrderItemQueryService.findActiveByIdIn(sourceSalesOrderItemIds).stream()
                .collect(java.util.stream.Collectors.toMap(SalesOrderItem::getId, item -> item));
    }

    private Map<Long, AuditedOutboundActual> loadAuditedOutboundActualMap(Set<Long> sourceSalesOrderItemIds) {
        if (salesOutboundRepository == null || sourceSalesOrderItemIds.isEmpty()) {
            return Map.of();
        }
        List<Long> sortedSourceItemIds = sourceSalesOrderItemIds.stream().sorted().toList();
        Map<Long, AuditedOutboundActual> result = new java.util.HashMap<>();
        salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                        StatusConstants.AUDITED,
                        sortedSourceItemIds
                ).stream()
                .flatMap(outbound -> outbound.getItems().stream())
                .filter(item -> item.getSourceSalesOrderItemId() != null)
                .filter(item -> sourceSalesOrderItemIds.contains(item.getSourceSalesOrderItemId()))
                .forEach(item -> result.merge(
                        item.getSourceSalesOrderItemId(),
                        AuditedOutboundActual.from(item),
                        AuditedOutboundActual::merge
                ));
        return Map.copyOf(result);
    }

    private AuditedOutboundActual resolveAuditedOutboundActual(SalesOrderItem sourceItem,
                                                               Map<Long, AuditedOutboundActual> actualMap,
                                                               int lineNo) {
        AuditedOutboundActual actual = actualMap.get(sourceItem.getId());
        if (actual == null && salesOutboundRepository != null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行来源销售订单明细没有已审核销售出库，不能生成客户对账单"
            );
        }
        return actual;
    }

    private void validateSourceSalesOrders(CustomerStatementRequest request,
                                           Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                           Long currentStatementId) {
        Map<Long, SalesOrder> requestedOrders = new java.util.LinkedHashMap<>();
        for (SalesOrderItem item : sourceSalesOrderItemMap.values()) {
            SalesOrder order = item.getSalesOrder();
            requestedOrders.put(order.getId(), order);
            BusinessDocumentValidator.requireSameText(
                    request.customerName(),
                    order.getCustomerName(),
                    "来源销售订单存在不同客户，不能合并生成客户对账单"
            );
            BusinessDocumentValidator.requireSameText(
                    request.projectName(),
                    order.getProjectName(),
                    "来源销售订单存在不同项目，不能合并生成客户对账单"
            );
            BusinessDocumentValidator.requireStatusIn(
                    order.getStatus(),
                    Set.of(StatusConstants.SALES_COMPLETED),
                    "来源销售订单" + order.getOrderNo() + "未完成销售，不能生成客户对账单"
            );
            if (request.settlementCompanyId() != null
                    && order.getSettlementCompanyId() != null
                    && !request.settlementCompanyId().equals(order.getSettlementCompanyId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售订单存在不同客户结算主体，不能合并生成客户对账单");
            }
            requireSameIdentity(request.customerId(), order.getCustomerId(), "客户ID与来源销售订单不一致");
            requireSameIdentity(request.projectId(), order.getProjectId(), "项目ID与来源销售订单不一致");
        }
        if (requestedOrders.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户对账单来源销售订单不能为空");
        }
        assertCompleteSourceItemCoverage(sourceSalesOrderItemMap.values());
        assertSourceOrdersNotOccupied(requestedOrders, currentStatementId);
    }

    private void assertCompleteSourceItemCoverage(Collection<SalesOrderItem> requestedItems) {
        requestedItems.stream()
                .map(SalesOrderItem::getSalesOrder)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(order -> StatementSourceCoverageValidator.requireAllEffectiveItems(
                        "来源销售订单" + order.getOrderNo(),
                        order.getItems().stream().map(SalesOrderItem::getId).toList(),
                        requestedItems.stream()
                                .filter(item -> sameSalesOrder(item.getSalesOrder(), order))
                                .map(SalesOrderItem::getId)
                                .toList()
                ));
    }

    private boolean sameSalesOrder(SalesOrder left, SalesOrder right) {
        if (left == right) {
            return true;
        }
        return left != null
                && right != null
                && left.getId() != null
                && left.getId().equals(right.getId());
    }

    private void assertSourceOrdersNotOccupied(Map<Long, SalesOrder> requestedOrders,
                                               Long currentStatementId) {
        Set<Long> occupiedOrderIds = toIdSet(
                repository.findMatchingOccupiedSourceSalesOrderIdsExcludingCurrentStatement(
                        requestedOrders.keySet(),
                        currentStatementId
                )
        );
        for (SalesOrder order : requestedOrders.values()) {
            if (occupiedOrderIds.contains(order.getId())) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "来源销售订单" + order.getOrderNo() + "已生成客户对账单"
                );
            }
        }
    }

    private SalesOrderItem resolveSourceSalesOrderItem(CustomerStatementItemRequest source,
                                                       Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                                       int lineNo) {
        Long sourceSalesOrderItemId = source.sourceSalesOrderItemId();
        if (sourceSalesOrderItemId != null) {
            SalesOrderItem sourceSalesOrderItem = sourceSalesOrderItemMap.get(sourceSalesOrderItemId);
            if (sourceSalesOrderItem == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不存在");
            }
            requireSameIdentity(source.customerId(), sourceSalesOrderItem.getSalesOrder().getCustomerId(),
                    "第" + lineNo + "行客户ID与来源销售订单不一致");
            requireSameIdentity(source.projectId(), sourceSalesOrderItem.getSalesOrder().getProjectId(),
                    "第" + lineNo + "行项目ID与来源销售订单不一致");
            requireSameIdentity(source.materialId(), sourceSalesOrderItem.getMaterialId(),
                    "第" + lineNo + "行商品ID与来源销售订单不一致");
            requireSameIdentity(source.warehouseId(), sourceSalesOrderItem.getWarehouseId(),
                    "第" + lineNo + "行仓库ID与来源销售订单不一致");
            return sourceSalesOrderItem;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不能为空");
    }

    private String resolveCustomerCode(String requestCustomerCode,
                                       String customerName,
                                       String projectName,
                                       Map<Long, SalesOrderItem> sourceSalesOrderItemMap) {
        String resolvedCode = trimToNull(requestCustomerCode);
        for (SalesOrderItem item : sourceSalesOrderItemMap.values()) {
            resolvedCode = mergeCustomerCode(resolvedCode, trimToNull(item.getSalesOrder().getCustomerCode()));
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

    private PartyIdentity resolvePartyIdentity(Collection<SalesOrderItem> sourceItems) {
        List<PartyIdentity> identities = sourceItems.stream()
                .map(SalesOrderItem::getSalesOrder)
                .filter(java.util.Objects::nonNull)
                .map(order -> new PartyIdentity(order.getCustomerId(), order.getProjectId()))
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

    private SettlementCompanySnapshot resolveStatementSettlementCompany(List<SalesOrder> orders) {
        List<SettlementCompanySnapshot> snapshots = orders.stream()
                .map(order -> new SettlementCompanySnapshot(order.getSettlementCompanyId(), trimToNull(order.getSettlementCompanyName())))
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

    private CustomerStatementCandidateResponse toCandidateResponse(SalesOrder order) {
        return new CustomerStatementCandidateResponse(
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

    private record AuditedOutboundActual(long quantity, BigDecimal weightTon, BigDecimal amount) {

        private static AuditedOutboundActual from(SalesOutboundItem item) {
            return new AuditedOutboundActual(
                    item.getQuantity() == null ? 0L : item.getQuantity().longValue(),
                    TradeItemCalculator.scaleWeightTon(item.getWeightTon()),
                    TradeItemCalculator.scaleAmount(item.getAmount())
            );
        }

        private AuditedOutboundActual merge(AuditedOutboundActual other) {
            return new AuditedOutboundActual(
                    Math.addExact(quantity, other.quantity),
                    TradeItemCalculator.scaleWeightTon(weightTon.add(other.weightTon)),
                    TradeItemCalculator.scaleAmount(amount.add(other.amount))
            );
        }
    }
}
