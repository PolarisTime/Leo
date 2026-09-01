package com.leo.erp.sales.order.service;

import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractStatusCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderOutboundCandidateQueryRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.repository.SalesOrderReferenceQueryRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class SalesOrderService extends AbstractStatusCrudService<SalesOrder, SalesOrderRequest, SalesOrderResponse> {
    private final DocumentChargeItemService documentChargeItemService;

    private static final String MODULE_KEY = "sales-order";


    private static final String[] PRODUCT_SEARCH_FIELDS = {"materialCode", "brand", "material", "spec"};

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
    private final BusinessOperationEventPublisher businessOperationEventPublisher;
    private final SalesOrderReferenceQueryRepository referenceQueryRepository;

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
                             SalesOrderOutboundCandidateQueryRepository outboundCandidateQueryRepository,
                             BusinessOperationEventPublisher businessOperationEventPublisher,
                             DocumentChargeItemService documentChargeItemService,
                             SalesOrderReferenceQueryRepository referenceQueryRepository) {
        super(idGenerator);
        this.documentChargeItemService = documentChargeItemService;
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
        this.businessOperationEventPublisher = businessOperationEventPublisher;
        this.referenceQueryRepository = referenceQueryRepository;
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> page(PageQuery query, PageFilter filter, String productKeyword) {
        return page(query, filter, productKeyword, null);
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> page(PageQuery query, PageFilter filter, String productKeyword, Boolean pendingOnly) {
        Page<SalesOrder> entities;
        if (Boolean.TRUE.equals(pendingOnly)) {
            entities = repository.findPending(
                    normalizeContains(filter.keyword()),
                    filter.customerId(),
                    normalizeExact(filter.name()),
                    filter.projectId(),
                    normalizeExact(filter.projectName()),
                    filter.settlementCompanyId(),
                    normalizeContains(productKeyword),
                    normalizeExact(filter.status()),
                    filter.startDate(),
                    filter.endDate(),
                    StatusConstants.SALES_COMPLETED,
                    query.toPageable("id")
            );
        } else {
            Specification<SalesOrder> spec = Specs.<SalesOrder>keywordLike(filter.keyword(), SALES_ORDER_SEARCH_FIELDS)
                    .and(Specs.collectionKeywordLike(productKeyword, "items", PRODUCT_SEARCH_FIELDS))
                    .and(Specs.equalIfPresent("customerName", filter.name()))
                    .and(Specs.equalIfPresent("projectName", filter.projectName()))
                    .and(Specs.equalValueIfPresent("customerId", filter.customerId()))
                    .and(Specs.equalValueIfPresent("projectId", filter.projectId()))
                    .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                    .and(Specs.documentStatus(filter.status()))
                    .and(Specs.betweenIfPresent("deliveryDate", filter.startDate(), filter.endDate()));
            entities = pageEntities(query, spec, repository);
        }
        Map<Long, SalesOrderReferenceQueryRepository.ReferenceStatus> statuses =
                referenceQueryRepository == null
                        ? Map.of()
                        : referenceQueryRepository.findByOrderIds(
                                entities.getContent().stream().map(SalesOrder::getId).toList());
        return entities.map(order -> {
            SalesOrderResponse response = toResponse(order);
            SalesOrderReferenceQueryRepository.ReferenceStatus status = statuses.get(order.getId());
            return status == null
                    ? response
                    : response.withReferenceFlags(
                            status.referencedByFreightBill(),
                            status.referencedBySalesOutbound());
        });
    }

    private static String normalizeContains(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeExact(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
    @Transactional
    public SalesOrderResponse create(SalesOrderRequest request) {
        SalesOrderResponse created = super.create(
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        applyChargeTotal(created.id(), BigDecimal.ZERO);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Override
    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderRequest request) {
        BigDecimal previousExpenseTotal = documentChargeItemService
                .sumAmount(documentChargeItemService.list(MODULE_KEY, id));
        SalesOrderResponse updated = super.update(id,
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        documentChargeItemService.sync(MODULE_KEY, id, request.chargeItems());
        applyChargeTotal(id, previousExpenseTotal);
        if (request.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
    }

    @Transactional
    public SalesOrderResponse updateAndComplete(Long id, SalesOrderRequest request) {
        super.update(id, withStatus(request, StatusConstants.DELIVERY_VERIFICATION));
        return completeSalesOrder(id);
    }

    @Override
    protected SalesOrderResponse toDetailResponse(SalesOrder entity) {
        SalesOrderResponse response = responseAssembler.toDetailResponse(entity);
        SalesOrderReferenceQueryRepository.ReferenceStatus status = referenceQueryRepository == null
                ? null
                : referenceQueryRepository.findByOrderIds(List.of(entity.getId())).get(entity.getId());
        return status == null
                ? response
                : response.withReferenceFlags(
                        status.referencedByFreightBill(),
                        status.referencedBySalesOutbound());
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
        assertOwnedByCurrentUser(entity);
        if (!entity.getOrderNo().equals(request.orderNo()) && repository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售订单号已存在");
        }
    }

    private void assertOwnedByCurrentUser(SalesOrder order) {
        Long currentUserId = requireCurrentUserId();
        Long ownerUserId = order.getOwnerUserId() == null ? order.getCreatedBy() : order.getOwnerUserId();
        if (!Objects.equals(ownerUserId, currentUserId)) {
            // 业务所有权是领域不变量，与创建审计和功能授权无关。
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能编辑本人负责的销售订单");
        }
    }

    private Long requireCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)
                || principal.id() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无法识别当前登录账号");
        }
        return principal.id();
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
                request.items(),
                request.chargeItems(),
                request.audit()
        );
    }

    /**
     * 单据总金额 = 货物明细小计 + 附加费用小计；totalWeight 永远仅货物。
     * 以「sync 前已落库的费用合计」做差额校正，避免二次保存重复计费。
     */
    private void applyChargeTotal(Long orderId, java.math.BigDecimal previousExpenseTotal) {
        SalesOrder order = requireEntity(orderId);
        java.math.BigDecimal currentExpense = documentChargeItemService
                .sumAmount(documentChargeItemService.list(MODULE_KEY, orderId));
        order.setTotalAmount(order.getTotalAmount()
                .subtract(previousExpenseTotal)
                .add(currentExpense));
    }

    private SalesOrderRequest withStatus(SalesOrderRequest request, String status) {
        return new SalesOrderRequest(
                request.orderNo(),
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
                status,
                request.remark(),
                request.items(),
                request.chargeItems(),
                request.audit()
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
                request.items(),
                request.chargeItems(),
                request.audit()
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
        assertOwnedByCurrentUser(order);
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
        assertOwnedByCurrentUser(entity);
        lockPurchaseSources(entity, null);
        if (downstreamMutationGuard != null) {
            downstreamMutationGuard.assertMutable(entity, "删除");
        }
    }

    @Override
    protected void afterDelete(SalesOrder entity) {
        documentChargeItemService.removeAll(MODULE_KEY, entity.getId());
        publishEvent(entity, "SALES_ORDER_DELETED", "删除", "删除销售订单 " + entity.getOrderNo());
    }

    @Override
    protected void beforeStatusUpdate(SalesOrder entity, String currentStatus, String nextStatus) {
        assertOwnedByCurrentUser(entity);
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
            assertAuditableLineQuantities(entity);
            salesOrderApplyService.validateCustomerSnapshot(entity);
        }
    }

    private void assertAuditableLineQuantities(SalesOrder entity) {
        for (SalesOrderItem item : entity.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + item.getLineNo() + "行数量必须至少为1个数量单位"
                );
            }
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
        SalesOrder order = new SalesOrder();
        order.setOwnerUserId(requireCurrentUserId());
        return order;
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
    protected java.util.Set<StatusTransition> allowedStatusTransitions() {
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
