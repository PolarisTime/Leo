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
import com.leo.erp.sales.order.repository.SalesOrderOutboundCandidateQueryRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final SalesOrderAuditedPricingService salesOrderAuditedPricingService;
    private final SalesOrderProtectedUpdatePolicy protectedUpdatePolicy;
    private final SalesOrderSaveService saveService;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final SalesOrderDeliveryVerificationGuard deliveryVerificationGuard;
    private final SalesOrderDownstreamMutationGuard downstreamMutationGuard;
    private final SalesOrderOutboundCandidateQueryRepository outboundCandidateQueryRepository;
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    @Autowired
    public SalesOrderService(SalesOrderRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             SalesOrderResponseAssembler responseAssembler,
                             SalesOrderApplyService salesOrderApplyService,
                             SalesOrderAuditedPricingService salesOrderAuditedPricingService,
                             SalesOrderProtectedUpdatePolicy protectedUpdatePolicy,
                             SalesOrderSaveService saveService,
                             SourceAllocationLockService sourceAllocationLockService,
                             SalesOrderDeliveryVerificationGuard deliveryVerificationGuard,
                             SalesOrderDownstreamMutationGuard downstreamMutationGuard,
                             SalesOrderOutboundCandidateQueryRepository outboundCandidateQueryRepository) {
        super(idGenerator);
        this.repository = repository;
        this.responseAssembler = responseAssembler;
        this.salesOrderApplyService = salesOrderApplyService;
        this.salesOrderAuditedPricingService = salesOrderAuditedPricingService;
        this.protectedUpdatePolicy = protectedUpdatePolicy;
        this.saveService = saveService;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.deliveryVerificationGuard = deliveryVerificationGuard;
        this.downstreamMutationGuard = downstreamMutationGuard;
        this.outboundCandidateQueryRepository = outboundCandidateQueryRepository;
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

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<SalesOrderResponse> outboundImportCandidates(PageQuery query, PageFilter filter) {
        Page<Long> candidateIds = outboundCandidateQueryRepository.pageIds(query, filter);
        List<SalesOrder> orders = candidateIds.isEmpty()
                ? List.of()
                : repository.findByIdInAndDeletedFlagFalse(candidateIds.getContent());
        java.util.Map<Long, SalesOrder> orderById = orders.stream()
                .collect(java.util.stream.Collectors.toMap(SalesOrder::getId, order -> order));
        List<SalesOrderResponse> candidates = candidateIds.getContent().stream()
                .map(orderById::get)
                .filter(Objects::nonNull)
                .map(responseAssembler::toDetailResponse)
                .toList();
        return new PageImpl<>(
                candidates,
                candidateIds.getPageable(),
                candidateIds.getTotalElements()
        );
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
        assertCreatedByCurrentUser(entity);
        if (!entity.getOrderNo().equals(request.orderNo()) && repository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售订单号已存在");
        }
    }

    private void assertCreatedByCurrentUser(SalesOrder order) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)
                || !Objects.equals(order.getCreatedBy(), principal.id())) {
            // 创建者约束是业务领域不变量，与功能授权无关。
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能编辑本人创建的销售订单");
        }
    }

    @Override
    protected SalesOrderRequest normalizeCreateRequest(SalesOrderRequest request, long entityId) {
        return new SalesOrderRequest(
                resolveCreateBusinessNo(entityId),
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
                hasLegacyPurchaseSource(entity) ? entity.getPurchaseInboundNo() : request.purchaseInboundNo(),
                hasLegacyPurchaseSource(entity) ? entity.getPurchaseOrderNo() : request.purchaseOrderNo(),
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

    private boolean hasLegacyPurchaseSource(SalesOrder entity) {
        return entity.getItems().stream()
                .anyMatch(item -> item.getSourcePurchaseOrderItemId() != null);
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
    protected boolean allowViewingDeletedRecords() {
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
