package com.leo.erp.sales.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class SalesOrderService extends AbstractCrudService<SalesOrder, SalesOrderRequest, SalesOrderResponse> {

    private final SalesOrderRepository repository;
    private final SalesOrderResponseAssembler responseAssembler;
    private final SalesOrderApplyService salesOrderApplyService;
    private final SalesOrderPurchaseAllocationService salesOrderPurchaseAllocationService;
    private final SalesOrderAuditedPricingService salesOrderAuditedPricingService;
    private final SalesOrderProtectedUpdatePolicy protectedUpdatePolicy;
    private final SalesOrderSaveService saveService;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final SalesOrderDeliveryVerificationGuard deliveryVerificationGuard;
    private final SalesOrderDownstreamMutationGuard downstreamMutationGuard;
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    public SalesOrderService(SalesOrderRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             SalesOrderResponseAssembler responseAssembler,
                             SalesOrderApplyService salesOrderApplyService,
                             SalesOrderPurchaseAllocationService salesOrderPurchaseAllocationService,
                             SalesOrderAuditedPricingService salesOrderAuditedPricingService,
                             SalesOrderProtectedUpdatePolicy protectedUpdatePolicy,
                             SalesOrderSaveService saveService,
                             SalesOrderItemRepository salesOrderItemRepository,
                             SourceAllocationLockService sourceAllocationLockService) {
        this(
                repository,
                idGenerator,
                responseAssembler,
                salesOrderApplyService,
                salesOrderPurchaseAllocationService,
                salesOrderAuditedPricingService,
                protectedUpdatePolicy,
                saveService,
                salesOrderItemRepository,
                sourceAllocationLockService,
                null
        );
    }

    public SalesOrderService(SalesOrderRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             SalesOrderResponseAssembler responseAssembler,
                             SalesOrderApplyService salesOrderApplyService,
                             SalesOrderPurchaseAllocationService salesOrderPurchaseAllocationService,
                             SalesOrderAuditedPricingService salesOrderAuditedPricingService,
                             SalesOrderProtectedUpdatePolicy protectedUpdatePolicy,
                             SalesOrderSaveService saveService,
                             SalesOrderItemRepository salesOrderItemRepository,
                             SourceAllocationLockService sourceAllocationLockService,
                             SalesOrderDeliveryVerificationGuard deliveryVerificationGuard) {
        this(
                repository,
                idGenerator,
                responseAssembler,
                salesOrderApplyService,
                salesOrderPurchaseAllocationService,
                salesOrderAuditedPricingService,
                protectedUpdatePolicy,
                saveService,
                salesOrderItemRepository,
                sourceAllocationLockService,
                deliveryVerificationGuard,
                null
        );
    }

    @Autowired
    public SalesOrderService(SalesOrderRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             SalesOrderResponseAssembler responseAssembler,
                             SalesOrderApplyService salesOrderApplyService,
                             SalesOrderPurchaseAllocationService salesOrderPurchaseAllocationService,
                             SalesOrderAuditedPricingService salesOrderAuditedPricingService,
                             SalesOrderProtectedUpdatePolicy protectedUpdatePolicy,
                             SalesOrderSaveService saveService,
                             SalesOrderItemRepository salesOrderItemRepository,
                             SourceAllocationLockService sourceAllocationLockService,
                             SalesOrderDeliveryVerificationGuard deliveryVerificationGuard,
                             SalesOrderDownstreamMutationGuard downstreamMutationGuard) {
        super(idGenerator);
        this.repository = repository;
        this.responseAssembler = responseAssembler;
        this.salesOrderApplyService = salesOrderApplyService;
        this.salesOrderPurchaseAllocationService = salesOrderPurchaseAllocationService;
        this.salesOrderAuditedPricingService = salesOrderAuditedPricingService;
        this.protectedUpdatePolicy = protectedUpdatePolicy;
        this.saveService = saveService;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.deliveryVerificationGuard = deliveryVerificationGuard;
        this.downstreamMutationGuard = downstreamMutationGuard;
    }

    @Autowired(required = false)
    void setBusinessOperationEventPublisher(BusinessOperationEventPublisher publisher) {
        this.businessOperationEventPublisher = publisher;
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> page(PageQuery query, PageFilter filter) {
        Specification<SalesOrder> spec = Specs.<SalesOrder>keywordLike(filter.keyword(), "orderNo", "purchaseOrderNo", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(Specs.equalValueIfPresent("customerId", filter.customerId()))
                .and(Specs.equalValueIfPresent("projectId", filter.projectId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("deliveryDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> outboundImportCandidates(PageQuery query, PageFilter filter) {
        Specification<SalesOrder> spec = Specs.<SalesOrder>keywordLike(filter.keyword(), SALES_ORDER_SEARCH_FIELDS)
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(Specs.equalValueIfPresent("customerId", filter.customerId()))
                .and(Specs.equalValueIfPresent("projectId", filter.projectId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", StatusConstants.AUDITED))
                .and(Specs.betweenIfPresent("deliveryDate", filter.startDate(), filter.endDate()));
        List<SalesOrder> orders = new java.util.ArrayList<>();
        int pageIndex = 0;
        Page<SalesOrder> orderPage;
        do {
            orderPage = pageEntities(
                    PageQuery.of(pageIndex, 200, query.sortBy(), query.direction()),
                    spec,
                    repository
            );
            orders.addAll(orderPage.getContent());
            pageIndex++;
        } while (orderPage.hasNext());

        java.util.Set<Long> occupiedItemIds = occupiedItemIds(orders, filter.currentRecordId());
        List<SalesOrderResponse> candidates = orders.stream()
                .filter(order -> StatusConstants.AUDITED.equals(order.getStatus()))
                .filter(order -> order.getItems().stream()
                        .map(SalesOrderItem::getId)
                        .allMatch(itemId -> itemId != null && !occupiedItemIds.contains(itemId)))
                .map(order -> responseAssembler.toDetailResponse(
                        order,
                        item -> item.getId() != null && !occupiedItemIds.contains(item.getId())
                ))
                .toList();
        int start = Math.min(query.page() * query.size(), candidates.size());
        int end = Math.min(start + query.size(), candidates.size());
        return new PageImpl<>(
                candidates.subList(start, end),
                query.toPageable("id"),
                candidates.size()
        );
    }

    private java.util.Set<Long> occupiedItemIds(List<SalesOrder> orders, Long currentOutboundId) {
        List<Long> itemIds = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(SalesOrderItem::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (itemIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(salesOrderItemRepository.findOccupiedSourceSalesOrderItemIds(
                itemIds,
                currentOutboundId
        ));
    }

    private static final String[] SALES_ORDER_SEARCH_FIELDS = {"orderNo", "purchaseOrderNo", "customerName", "projectName"};

    @Transactional(readOnly = true)
    public java.util.List<SalesOrderResponse> search(String keyword, int maxSize) {
        return search(keyword, SALES_ORDER_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected SalesOrderResponse toDetailResponse(SalesOrder entity) {
        return responseAssembler.toDetailResponse(entity);
    }

    @Override
    protected void validateCreate(SalesOrderRequest request) {
        if (repository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售订单号已存在");
        }
        String requestedStatus = normalizeStatus(request.status());
        if (!requestedStatus.isEmpty() && !StatusConstants.DRAFT.equals(requestedStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新销售订单只能保存为草稿，审核必须通过状态操作完成");
        }
    }

    @Override
    protected void validateUpdate(SalesOrder entity, SalesOrderRequest request) {
        if (!entity.getOrderNo().equals(request.orderNo()) && repository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售订单号已存在");
        }
    }

    @Override
    protected SalesOrderRequest normalizeCreateRequest(SalesOrderRequest request, long entityId) {
        return new SalesOrderRequest(
                resolveCreateBusinessNo("sales-order", request.orderNo(), entityId),
                request.purchaseInboundNo(),
                request.purchaseOrderNo(),
                request.customerCode(),
                request.customerId(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.deliveryDate(),
                request.salesName(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected SalesOrderRequest normalizeUpdateRequest(SalesOrder entity, SalesOrderRequest request) {
        assertOrdinaryUpdateKeepsStatus(entity.getStatus(), request.status());
        return new SalesOrderRequest(
                entity.getOrderNo(),
                request.purchaseInboundNo(),
                request.purchaseOrderNo(),
                request.customerCode(),
                request.customerId(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.deliveryDate(),
                request.salesName(),
                entity.getStatus(),
                request.remark(),
                request.items()
        );
    }

    private void assertOrdinaryUpdateKeepsStatus(String currentStatus, String requestedStatus) {
        String normalizedRequestedStatus = normalizeStatus(requestedStatus);
        if (!normalizedRequestedStatus.isEmpty() && !Objects.equals(currentStatus, normalizedRequestedStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售订单状态只能通过审核、反审核或完成销售操作变更");
        }
    }

    @Transactional
    public SalesOrderResponse completeSalesOrder(Long id) {
        SalesOrder order = repository.findForUpdateByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        String currentStatus = normalizeStatus(order.getStatus());
        if (StatusConstants.SALES_COMPLETED.equals(currentStatus)) {
            return toDetailResponse(order);
        }
        if (!StatusConstants.DELIVERY_VERIFICATION.equals(currentStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "只有交付核定状态可以完成销售");
        }
        salesOrderApplyService.assertCompletionPermission(order);
        salesOrderApplyService.validateCustomerSnapshot(order);
        order.setStatus(StatusConstants.SALES_COMPLETED);
        SalesOrder saved = saveService.saveStatus(order);
        publishEvent(saved, "SALES_ORDER_COMPLETED", "完成销售",
                "销售订单状态 " + currentStatus + " -> " + saved.getStatus());
        return toDetailResponse(saved);
    }

    @Override
    protected void beforeDelete(SalesOrder entity) {
        lockPurchaseSources(entity, null);
        if (downstreamMutationGuard != null) {
            downstreamMutationGuard.assertMutable(entity, "删除");
        }
        salesOrderPurchaseAllocationService.releaseSalesOrderItems(entity);
    }

    @Override
    protected void afterDelete(SalesOrder entity) {
        publishEvent(entity, "SALES_ORDER_DELETED", "删除", "删除销售订单 " + entity.getOrderNo());
    }

    @Override
    protected void beforeStatusUpdate(SalesOrder entity, String currentStatus, String nextStatus) {
        lockPurchaseSources(entity, null);
        if (StatusConstants.SALES_COMPLETED.equals(nextStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "完成销售必须通过专用完成操作执行");
        }
        if (StatusConstants.SALES_COMPLETED.equals(currentStatus)
                && StatusConstants.DELIVERY_VERIFICATION.equals(nextStatus)) {
            deliveryVerificationGuard.assertMutable(entity, "反审核");
        }
        salesOrderApplyService.assertStatusPermission(entity, nextStatus);
        if (downstreamMutationGuard != null
                && StatusConstants.DRAFT.equals(nextStatus)
                && !StatusConstants.DRAFT.equals(currentStatus)) {
            downstreamMutationGuard.assertMutable(entity, "反审核");
        }
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            salesOrderApplyService.validateCustomerSnapshot(entity);
        }
    }

    private void lockPurchaseSources(SalesOrder entity, SalesOrderRequest request) {
        Stream<SalesOrderItem> existingItems = entity == null
                ? Stream.empty()
                : entity.getItems().stream();
        List<SalesOrderItem> currentItems = existingItems.toList();
        Stream<SalesOrderItemRequest> requestedItems = request == null
                ? Stream.empty()
                : request.items().stream();
        List<SalesOrderItemRequest> nextItems = requestedItems.toList();
        List<Long> purchaseOrderItemIds = Stream.concat(
                        currentItems.stream().map(SalesOrderItem::getSourcePurchaseOrderItemId),
                        nextItems.stream().map(SalesOrderItemRequest::sourcePurchaseOrderItemId)
                )
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        List<Long> purchaseInboundItemIds = Stream.concat(
                        currentItems.stream().map(SalesOrderItem::getSourceInboundItemId),
                        nextItems.stream().map(SalesOrderItemRequest::sourceInboundItemId)
                )
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        sourceAllocationLockService.lockTradeItemSources(
                purchaseOrderItemIds,
                purchaseInboundItemIds,
                List.of()
        );
    }

    @Override
    protected SalesOrder newEntity() {
        return new SalesOrder();
    }

    @Override
    protected void assignId(SalesOrder entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<SalesOrder> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<SalesOrder> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "销售订单不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return StatusConstants.SALES_ORDER_TRANSITIONS;
    }

    @Override
    @Transactional
    public SalesOrderResponse updateStatus(Long id, String status) {
        SalesOrder order = requireEntity(id);
        String currentStatus = order.getStatus();
        SalesOrderResponse response = super.updateStatus(id, status);
        if (!Objects.equals(currentStatus, response.status())) {
            String actionType = resolveStatusAction(currentStatus, response.status());
            publishEvent(order, "SALES_ORDER_STATUS_CHANGED", actionType,
                    "销售订单状态 " + currentStatus + " -> " + response.status());
        }
        return response;
    }

    @Override
    protected boolean allowRequestToWriteFinalStatus(SalesOrder entity,
                                                     SalesOrderRequest request,
                                                     Optional<String> currentStatus) {
        return currentStatus.filter(StatusConstants.DELIVERY_VERIFICATION::equals).isPresent()
                && StatusConstants.DELIVERY_VERIFICATION.equals(request.status())
                && StatusConstants.DELIVERY_VERIFICATION.equals(entity.getStatus());
    }

    @Override
    protected boolean allowProtectedStatusUpdate(SalesOrder entity, SalesOrderRequest request) {
        return protectedUpdatePolicy.allowsProtectedUpdate(entity, request);
    }

    @Override
    protected void apply(SalesOrder entity, SalesOrderRequest request) {
        lockPurchaseSources(entity, request);
        boolean auditedPricingUpdate = salesOrderAuditedPricingService.isAuditedPricingUpdate(entity, request);
        if (entity.getItems().stream().anyMatch(item -> item.getId() != null)
                && !auditedPricingUpdate
                && downstreamMutationGuard != null) {
            downstreamMutationGuard.assertNoFreightReference(entity, "修改");
        }
        if (!auditedPricingUpdate
                && entity.getItems().stream().anyMatch(item -> item.getId() != null)
                && downstreamMutationGuard != null) {
            downstreamMutationGuard.assertSourceLineMutationAllowed(entity, request.items(), "修改");
        }
        if (entity.getId() != null
                && StatusConstants.DELIVERY_VERIFICATION.equals(entity.getStatus())
                && deliveryVerificationGuard != null) {
            deliveryVerificationGuard.assertMutable(entity, "修改");
        }
        if (auditedPricingUpdate) {
            salesOrderApplyService.validateCustomerSnapshot(request);
            salesOrderAuditedPricingService.applyAuditedPricingUpdate(entity, request);
            return;
        }
        salesOrderApplyService.apply(entity, request, this::nextId);
    }

    @Override
    protected SalesOrder saveEntity(SalesOrder entity) {
        return saveService.save(entity);
    }

    @Override
    protected SalesOrder saveCreatedEntity(SalesOrder entity, SalesOrderRequest request) {
        SalesOrder saved = saveEntity(entity);
        publishEvent(saved, "SALES_ORDER_CREATED", "新增", "新增销售订单 " + saved.getOrderNo());
        return saved;
    }

    @Override
    protected SalesOrder saveUpdatedEntity(SalesOrder entity, SalesOrderRequest request) {
        SalesOrder saved;
        if (salesOrderAuditedPricingService.isAuditedPricingUpdate(entity, request)) {
            saved = saveService.saveAuditedPricingUpdate(entity);
        } else {
            saved = saveEntity(entity);
        }
        publishEvent(saved, "SALES_ORDER_UPDATED", "编辑", "编辑销售订单 " + saved.getOrderNo());
        return saved;
    }

    @Override
    protected SalesOrder saveStatusEntity(SalesOrder entity) {
        return saveService.saveStatus(entity);
    }

    @Override
    protected SalesOrderResponse toResponse(SalesOrder entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

    @Override
    protected SalesOrderResponse toSavedResponse(SalesOrder entity) {
        return toDetailResponse(entity);
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolveStatusAction(String currentStatus, String nextStatus) {
        if (StatusConstants.DRAFT.equals(currentStatus) && StatusConstants.AUDITED.equals(nextStatus)) {
            return "审核";
        }
        if (StatusConstants.DRAFT.equals(nextStatus)) {
            return "反审核";
        }
        return "状态变更";
    }

    private void publishEvent(SalesOrder order, String eventType, String actionType, String remark) {
        if (businessOperationEventPublisher == null) {
            return;
        }
        businessOperationEventPublisher.publish(
                eventType,
                "sales-order",
                "销售订单",
                actionType,
                "SalesOrder",
                order.getId(),
                order.getOrderNo(),
                remark
        );
    }

}
