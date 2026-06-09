package com.leo.erp.statement.customer.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.mapper.CustomerStatementMapper;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import com.leo.erp.statement.service.StatementCandidateSupport;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class CustomerStatementService extends AbstractCrudService<CustomerStatement, CustomerStatementRequest, CustomerStatementResponse> {

    private static final String[] SALES_ORDER_CANDIDATE_SEARCH_FIELDS = {
            "orderNo",
            "purchaseInboundNo",
            "purchaseOrderNo",
            "customerName",
            "projectName",
            "salesName"
    };

    private final CustomerStatementRepository repository;
    private final CustomerStatementMapper customerStatementMapper;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemQueryService salesOrderItemQueryService;
    private final StatementSettlementSyncService statementSettlementSyncService;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final CustomerRepository customerRepository;

    @Autowired
    public CustomerStatementService(CustomerStatementRepository repository,
                                    SnowflakeIdGenerator idGenerator,
                                    CustomerStatementMapper customerStatementMapper,
                                    SalesOrderRepository salesOrderRepository,
                                    SalesOrderItemQueryService salesOrderItemQueryService,
                                    StatementSettlementSyncService statementSettlementSyncService,
                                    WorkflowTransitionGuard workflowTransitionGuard,
                                    CustomerRepository customerRepository) {
        super(idGenerator);
        this.repository = repository;
        this.customerStatementMapper = customerStatementMapper;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderItemQueryService = salesOrderItemQueryService;
        this.statementSettlementSyncService = statementSettlementSyncService;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.customerRepository = customerRepository;
    }

    public CustomerStatementService(CustomerStatementRepository repository,
                                    SnowflakeIdGenerator idGenerator,
                                    CustomerStatementMapper customerStatementMapper,
                                    SalesOrderRepository salesOrderRepository,
                                    SalesOrderItemQueryService salesOrderItemQueryService,
                                    StatementSettlementSyncService statementSettlementSyncService,
                                    WorkflowTransitionGuard workflowTransitionGuard) {
        this(repository, idGenerator, customerStatementMapper, salesOrderRepository, salesOrderItemQueryService,
                statementSettlementSyncService, workflowTransitionGuard, null);
    }

    @Transactional(readOnly = true)
    public Page<CustomerStatementResponse> page(PageQuery query, PageFilter filter) {
        Specification<CustomerStatement> spec = Specs.<CustomerStatement>keywordLike(filter.keyword(), "statementNo", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("endDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    private static final String[] CUSTOMER_STATEMENT_SEARCH_FIELDS = {
            "statementNo",
            "customerName",
            "projectName"
    };

    @Transactional(readOnly = true)
    public List<CustomerStatementResponse> search(String keyword, int maxSize) {
        return search(keyword, CUSTOMER_STATEMENT_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Transactional(readOnly = true)
    public Page<CustomerStatementCandidateResponse> candidatePage(PageQuery query, PageFilter filter) {
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

    @Override
    protected CustomerStatementResponse toDetailResponse(CustomerStatement entity) {
        CustomerStatementResponse response = customerStatementMapper.toResponse(entity);
        return new CustomerStatementResponse(
                response.id(),
                response.statementNo(),
                response.customerCode(),
                response.customerName(),
                response.projectId(),
                response.projectName(),
                response.startDate(),
                response.endDate(),
                response.salesAmount(),
                response.receiptAmount(),
                response.closingAmount(),
                response.status(),
                response.remark(),
                entity.getItems().stream().map(this::toItemResponse).toList()
        );
    }

    @Override
    protected CustomerStatementResponse toSavedResponse(CustomerStatement entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void validateCreate(CustomerStatementRequest request) {
        if (repository.existsByStatementNoAndDeletedFlagFalse(request.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户对账单号已存在");
        }
    }

    @Override
    protected void validateUpdate(CustomerStatement entity, CustomerStatementRequest request) {
        if (!entity.getStatementNo().equals(request.statementNo())
                && repository.existsByStatementNoAndDeletedFlagFalse(request.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户对账单号已存在");
        }
    }

    @Override
    protected CustomerStatementRequest normalizeCreateRequest(CustomerStatementRequest request, long entityId) {
        return new CustomerStatementRequest(
                resolveCreateBusinessNo("customer-statement", request.statementNo(), entityId),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.startDate(),
                request.endDate(),
                request.salesAmount(),
                request.receiptAmount(),
                request.closingAmount(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected CustomerStatementRequest normalizeUpdateRequest(CustomerStatement entity, CustomerStatementRequest request) {
        return new CustomerStatementRequest(
                entity.getStatementNo(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.startDate(),
                request.endDate(),
                request.salesAmount(),
                request.receiptAmount(),
                request.closingAmount(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected CustomerStatement newEntity() {
        return new CustomerStatement();
    }

    @Override
    protected void assignId(CustomerStatement entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<CustomerStatement> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<CustomerStatement> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "客户对账单不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected Set<String> allowedStatusTransitions() {
        return StatusConstants.STATEMENT_CONFIRM_TRANSITIONS;
    }

    @Override
    protected void beforeStatusUpdate(CustomerStatement entity, String currentStatus, String nextStatus) {
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "customer-statement",
                currentStatus,
                nextStatus,
                StatusConstants.CONFIRMED
        );
    }

    @Override
    protected void apply(CustomerStatement entity, CustomerStatementRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.PENDING_CONFIRM,
                "客户对账单状态",
                StatusConstants.ALLOWED_STATEMENT_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "customer-statement",
                entity.getStatus(),
                nextStatus,
                StatusConstants.CONFIRMED
        );
        entity.setStatementNo(request.statementNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setProjectId(request.projectId());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

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
                this::nextId,
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
        BigDecimal receiptAmount = request.receiptAmount() == null
                ? BigDecimal.ZERO
                : TradeItemCalculator.scaleAmount(request.receiptAmount());
        if (receiptAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户对账单收款金额不能为负数");
        }
        if (receiptAmount.compareTo(salesAmount) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户对账单销售金额不能低于已收款金额");
        }
        entity.setSalesAmount(salesAmount);
        entity.setReceiptAmount(receiptAmount);
        entity.setClosingAmount(TradeItemCalculator.scaleAmount(salesAmount.subtract(receiptAmount).max(BigDecimal.ZERO)));
    }

    @Override
    protected CustomerStatement saveEntity(CustomerStatement entity) {
        return repository.save(entity);
    }

    @Override
    protected CustomerStatementResponse toResponse(CustomerStatement entity) {
        return customerStatementMapper.toResponse(entity);
    }

    private CustomerStatementItemResponse toItemResponse(CustomerStatementItem item) {
        return new CustomerStatementItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceNo(),
                item.getSourceSalesOrderItemId(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getBatchNo(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                item.getUnitPrice(),
                item.getAmount()
        );
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
            if (!request.customerName().trim().equals(order.getCustomerName())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售订单存在不同客户，不能合并生成客户对账单");
            }
            if (!request.projectName().trim().equals(order.getProjectName())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售订单存在不同项目，不能合并生成客户对账单");
            }
            if (!StatusConstants.SALES_COMPLETED.equals(order.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售订单" + order.getOrderNo() + "未完成销售，不能生成客户对账单");
            }
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

    private Set<String> collectOccupiedOrderNos(Long currentStatementId) {
        Specification<CustomerStatement> spec = Specs.notDeleted();
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
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
