package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.outbound.web.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesOutboundService extends AbstractCrudService<SalesOutbound, SalesOutboundRequest, SalesOutboundResponse> {

    private final SalesOutboundRepository repository;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final SalesOutboundApplyService salesOutboundApplyService;
    private final SalesOutboundResponseAssembler responseAssembler;
    private final SalesOutboundSaveService saveService;
    private final SalesOutboundPurchaseInboundGuard purchaseInboundGuard;
    private final SourceAllocationLockService sourceAllocationLockService;

    @Autowired
    public SalesOutboundService(SalesOutboundRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                WorkflowTransitionGuard workflowTransitionGuard,
                                SalesOutboundApplyService salesOutboundApplyService,
                                SalesOutboundResponseAssembler responseAssembler,
                                SalesOutboundSaveService saveService,
                                SalesOutboundPurchaseInboundGuard purchaseInboundGuard,
                                SourceAllocationLockService sourceAllocationLockService) {
        super(idGenerator);
        this.repository = repository;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.salesOutboundApplyService = salesOutboundApplyService;
        this.responseAssembler = responseAssembler;
        this.saveService = saveService;
        this.purchaseInboundGuard = purchaseInboundGuard;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    @Transactional(readOnly = true)
    public Page<SalesOutboundResponse> page(PageQuery query, PageFilter filter) {
        Specification<SalesOutbound> spec = Specs.<SalesOutbound>keywordLike(filter.keyword(), "outboundNo", "salesOrderNo", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("outboundDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    private static final String[] OUTBOUND_SEARCH_FIELDS = {"outboundNo", "salesOrderNo", "customerName", "projectName"};

    @Transactional(readOnly = true)
    public java.util.List<SalesOutboundResponse> search(String keyword, int maxSize) {
        return search(keyword, OUTBOUND_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected SalesOutboundResponse toDetailResponse(SalesOutbound entity) {
        return responseAssembler.toDetailResponse(entity);
    }

    @Override
    protected void validateCreate(SalesOutboundRequest request) {
        if (repository.existsByOutboundNoAndDeletedFlagFalse(request.outboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库单号已存在");
        }
    }

    @Override
    protected void validateUpdate(SalesOutbound entity, SalesOutboundRequest request) {
        if (!entity.getOutboundNo().equals(request.outboundNo()) && repository.existsByOutboundNoAndDeletedFlagFalse(request.outboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库单号已存在");
        }
    }

    @Override
    protected SalesOutboundRequest normalizeCreateRequest(SalesOutboundRequest request, long entityId) {
        return new SalesOutboundRequest(
                resolveCreateBusinessNo("sales-outbound", request.outboundNo(), entityId),
                null,
                request.customerName(),
                request.projectName(),
                request.warehouseName(),
                request.outboundDate(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected SalesOutboundRequest normalizeUpdateRequest(SalesOutbound entity, SalesOutboundRequest request) {
        if (hasImportedSalesOrder(entity)) {
            return restrictImportedOutboundUpdate(entity, request);
        }
        return new SalesOutboundRequest(
                entity.getOutboundNo(),
                entity.getSalesOrderNo(),
                request.customerName(),
                request.projectName(),
                request.warehouseName(),
                request.outboundDate(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    private boolean hasImportedSalesOrder(SalesOutbound entity) {
        if (entity.getSalesOrderNo() != null && !entity.getSalesOrderNo().isBlank()) {
            return true;
        }
        return entity.getItems().stream()
                .anyMatch(item -> item.getSourceSalesOrderItemId() != null);
    }

    private SalesOutboundRequest restrictImportedOutboundUpdate(SalesOutbound entity, SalesOutboundRequest request) {
        Map<Long, SalesOutboundItemRequest> requestItemsById = request.items().stream()
                .filter(item -> item.id() != null)
                .collect(Collectors.toMap(
                        SalesOutboundItemRequest::id,
                        Function.identity(),
                        (left, right) -> left
                ));
        List<SalesOutboundItemRequest> restrictedItems = entity.getItems().stream()
                .sorted(java.util.Comparator.comparing(SalesOutboundItem::getLineNo, java.util.Comparator.nullsLast(Integer::compareTo)))
                .map(item -> restrictImportedOutboundItem(item, requestItemsById.get(item.getId())))
                .toList();
        return new SalesOutboundRequest(
                entity.getOutboundNo(),
                entity.getSalesOrderNo(),
                entity.getCustomerName(),
                entity.getProjectName(),
                entity.getWarehouseName(),
                request.outboundDate(),
                request.status(),
                request.remark(),
                restrictedItems
        );
    }

    private SalesOutboundItemRequest restrictImportedOutboundItem(
            SalesOutboundItem item,
            SalesOutboundItemRequest requestItem
    ) {
        Integer nextQuantity = requestItem == null ? item.getQuantity() : requestItem.quantity();
        return new SalesOutboundItemRequest(
                item.getId(),
                null,
                item.getSourceSalesOrderItemId(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getWarehouseName(),
                item.getBatchNo(),
                nextQuantity,
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                item.getUnitPrice(),
                item.getAmount()
        );
    }

    @Override
    protected SalesOutbound newEntity() {
        return new SalesOutbound();
    }

    @Override
    protected void assignId(SalesOutbound entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<SalesOutbound> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<SalesOutbound> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "销售出库不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return StatusConstants.SALES_OUTBOUND_TRANSITIONS;
    }

    @Override
    protected void apply(SalesOutbound entity, SalesOutboundRequest request) {
        lockSourceSalesOrderItems(entity.getItems(), request.items());
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "销售出库状态",
                StatusConstants.ALLOWED_SALES_OUTBOUND_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "sales-outbound",
                entity.getStatus(),
                nextStatus,
                StatusConstants.AUDITED
        );
        entity.setOutboundNo(request.outboundNo());
        entity.setSalesOrderNo(request.salesOrderNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setOutboundDate(request.outboundDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());
        salesOutboundApplyService.applyItems(entity, request, this::nextId);
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            purchaseInboundGuard.assertPurchaseInboundCompletedBeforeAudit(entity);
        }
    }

    @Override
    protected void beforeStatusUpdate(SalesOutbound entity, String currentStatus, String nextStatus) {
        lockSourceSalesOrderItems(entity.getItems(), List.of());
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            purchaseInboundGuard.assertPurchaseInboundCompletedBeforeAudit(entity);
        }
    }

    @Override
    protected void beforeDelete(SalesOutbound entity) {
        lockSourceSalesOrderItems(entity.getItems(), List.of());
    }

    private void lockSourceSalesOrderItems(List<SalesOutboundItem> existingItems,
                                           List<SalesOutboundItemRequest> requestedItems) {
        TreeSet<Long> sourceIds = new TreeSet<>();
        existingItems.stream()
                .map(SalesOutboundItem::getSourceSalesOrderItemId)
                .filter(java.util.Objects::nonNull)
                .forEach(sourceIds::add);
        requestedItems.stream()
                .map(SalesOutboundItemRequest::sourceSalesOrderItemId)
                .filter(java.util.Objects::nonNull)
                .forEach(sourceIds::add);
        sourceAllocationLockService.lockTradeItemSources(List.of(), List.of(), List.copyOf(sourceIds));
    }

    @Override
    protected SalesOutbound saveEntity(SalesOutbound entity) {
        return saveService.save(entity);
    }

    @Override
    protected SalesOutboundResponse toResponse(SalesOutbound entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

    @Override
    protected SalesOutboundResponse toSavedResponse(SalesOutbound entity) {
        return toDetailResponse(entity);
    }
}
