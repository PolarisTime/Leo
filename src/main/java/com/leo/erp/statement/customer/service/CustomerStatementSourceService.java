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
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.service.StatementCandidateSupport;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    public CustomerStatementSourceService(CustomerStatementRepository repository,
                                          SalesOrderRepository salesOrderRepository,
                                          SalesOrderItemQueryService salesOrderItemQueryService,
                                          CustomerRepository customerRepository) {
        this.repository = repository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderItemQueryService = salesOrderItemQueryService;
        this.customerRepository = customerRepository;
    }

    Page<CustomerStatementCandidateResponse> candidatePage(PageQuery query, PageFilter filter) {
        Set<String> occupiedOrderNos = collectOccupiedOrderNos(null);
        Specification<SalesOrder> spec = Specs.<SalesOrder>notDeleted()
                .and(Specs.keywordLike(filter.keyword(), SALES_ORDER_CANDIDATE_SEARCH_FIELDS))
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(Specs.equalIfPresent("status", StatusConstants.SALES_COMPLETED))
                .and(Specs.betweenIfPresent("deliveryDate", filter.startDate(), filter.endDate()))
                .and(StatementCandidateSupport.excludeFieldValues("orderNo", occupiedOrderNos));
        return salesOrderRepository.findAll(DataScopeContext.apply(spec), query.toPageable("id"))
                .map(this::toCandidateResponse);
    }

    BigDecimal applyItems(CustomerStatement entity,
                          CustomerStatementRequest request,
                          LongSupplier nextIdSupplier) {
        BigDecimal salesAmount = BigDecimal.ZERO;
        Map<Long, SalesOrderItem> sourceSalesOrderItemMap = loadSourceSalesOrderItemMap(request.items());
        validateSourceSalesOrders(request, sourceSalesOrderItemMap, entity.getId());
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
            CustomerStatementItem item = items.get(i);
            item.setCustomerStatement(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(sourceSalesOrderItem.getSalesOrder().getOrderNo());
            item.setSourceSalesOrderItemId(sourceSalesOrderItem.getId());
            item.setMaterialCode(sourceSalesOrderItem.getMaterialCode());
            item.setBrand(sourceSalesOrderItem.getBrand());
            item.setCategory(sourceSalesOrderItem.getCategory());
            item.setMaterial(sourceSalesOrderItem.getMaterial());
            item.setSpec(sourceSalesOrderItem.getSpec());
            item.setLength(sourceSalesOrderItem.getLength());
            item.setUnit(sourceSalesOrderItem.getUnit());
            item.setBatchNo(sourceSalesOrderItem.getBatchNo());
            item.setQuantity(sourceSalesOrderItem.getQuantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(sourceSalesOrderItem.getQuantityUnit()));
            item.setPieceWeightTon(TradeItemCalculator.scaleWeightTon(sourceSalesOrderItem.getPieceWeightTon()));
            item.setPiecesPerBundle(sourceSalesOrderItem.getPiecesPerBundle());
            item.setWeightTon(TradeItemCalculator.scaleWeightTon(sourceSalesOrderItem.getWeightTon()));
            item.setUnitPrice(TradeItemCalculator.scaleAmount(sourceSalesOrderItem.getUnitPrice()));
            BigDecimal amount = TradeItemCalculator.scaleAmount(sourceSalesOrderItem.getAmount());
            item.setAmount(amount);
            salesAmount = salesAmount.add(amount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(CustomerStatementItem::getLineNo));
        return salesAmount;
    }

    Set<String> collectOccupiedOrderNos(Long currentStatementId) {
        org.springframework.data.jpa.domain.Specification<CustomerStatement> spec =
                com.leo.erp.common.persistence.Specs.notDeleted();
        if (currentStatementId != null) {
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("id"), currentStatementId));
        }
        Set<String> occupiedOrderNos = new LinkedHashSet<>();
        repository.findAll(spec).stream()
                .flatMap(entity -> entity.getItems().stream())
                .map(CustomerStatementItem::getSourceNo)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .forEach(occupiedOrderNos::add);
        return occupiedOrderNos;
    }

    private Map<Long, SalesOrderItem> loadSourceSalesOrderItemMap(List<CustomerStatementItemRequest> items) {
        List<Long> sourceSalesOrderItemIds = items.stream()
                .map(CustomerStatementItemRequest::sourceSalesOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (sourceSalesOrderItemIds.isEmpty()) {
            return Map.of();
        }
        return salesOrderItemQueryService.findActiveByIdIn(sourceSalesOrderItemIds).stream()
                .collect(java.util.stream.Collectors.toMap(SalesOrderItem::getId, item -> item));
    }

    private void validateSourceSalesOrders(CustomerStatementRequest request,
                                           Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                           Long currentStatementId) {
        Set<String> requestedOrderNos = new LinkedHashSet<>();
        for (SalesOrderItem item : sourceSalesOrderItemMap.values()) {
            SalesOrder order = item.getSalesOrder();
            DataScopeContext.assertCanAccess(order);
            requestedOrderNos.add(order.getOrderNo());
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
        }
        if (requestedOrderNos.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户对账单来源销售订单不能为空");
        }
        assertSourceOrdersNotOccupied(requestedOrderNos, currentStatementId);
    }

    private void assertSourceOrdersNotOccupied(Set<String> requestedOrderNos, Long currentStatementId) {
        List<CustomerStatement> occupiedStatements =
                repository.findAllBySourceNosExcludingCurrentStatement(requestedOrderNos, currentStatementId);
        Set<String> occupiedOrderNos = occupiedStatements.stream()
                .flatMap(entity -> entity.getItems().stream())
                .map(CustomerStatementItem::getSourceNo)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String orderNo : requestedOrderNos) {
            if (occupiedOrderNos.contains(orderNo)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售订单" + orderNo + "已生成客户对账单");
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

    private CustomerStatementCandidateResponse toCandidateResponse(SalesOrder order) {
        return new CustomerStatementCandidateResponse(
                order.getId(),
                order.getOrderNo(),
                order.getCustomerName(),
                order.getProjectName(),
                order.getDeliveryDate(),
                order.getSalesName(),
                order.getTotalWeight(),
                order.getTotalAmount(),
                order.getStatus()
        );
    }
}
