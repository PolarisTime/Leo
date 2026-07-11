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
import com.leo.erp.finance.common.service.InvoiceSourceMutationGuard;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
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
    private final InvoiceSourceMutationGuard invoiceSourceMutationGuard;
    private final SalesOrderDeliveryVerificationGuard deliveryVerificationGuard;

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
                null,
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
                             InvoiceSourceMutationGuard invoiceSourceMutationGuard,
                             SalesOrderDeliveryVerificationGuard deliveryVerificationGuard) {
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
        this.invoiceSourceMutationGuard = invoiceSourceMutationGuard;
        this.deliveryVerificationGuard = deliveryVerificationGuard;
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> page(PageQuery query, PageFilter filter) {
        Specification<SalesOrder> spec = Specs.<SalesOrder>keywordLike(filter.keyword(), "orderNo", "purchaseOrderNo", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
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

        java.util.Set<Long> occupiedItemIds = occupiedItemIds(orders);
        List<SalesOrderResponse> candidates = orders.stream()
                .filter(order -> StatusConstants.AUDITED.equals(order.getStatus()))
                .filter(order -> order.getItems().stream()
                        .map(SalesOrderItem::getId)
                        .anyMatch(itemId -> itemId != null && !occupiedItemIds.contains(itemId)))
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

    private java.util.Set<Long> occupiedItemIds(List<SalesOrder> orders) {
        List<Long> itemIds = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(SalesOrderItem::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (itemIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(salesOrderItemRepository.findOccupiedSourceSalesOrderItemIds(itemIds));
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
        return new SalesOrderRequest(
                entity.getOrderNo(),
                request.purchaseInboundNo(),
                request.purchaseOrderNo(),
                request.customerCode(),
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
    protected void beforeDelete(SalesOrder entity) {
        lockPurchaseSources(entity, null);
        if (invoiceSourceMutationGuard != null) {
            invoiceSourceMutationGuard.assertSalesOrderMutable(entity, "删除");
        }
        salesOrderPurchaseAllocationService.releaseSalesOrderItems(entity);
    }

    @Override
    protected void beforeStatusUpdate(SalesOrder entity, String currentStatus, String nextStatus) {
        lockPurchaseSources(entity, null);
        if (StatusConstants.AUDITED.equals(nextStatus)
                || StatusConstants.SALES_COMPLETED.equals(nextStatus)) {
            salesOrderApplyService.validateCustomerSnapshot(entity);
        }
        if (deliveryVerificationGuard != null
                && StatusConstants.SALES_COMPLETED.equals(currentStatus)
                && StatusConstants.DELIVERY_VERIFICATION.equals(nextStatus)) {
            deliveryVerificationGuard.assertMutable(entity, "重新核定");
        }
        if (invoiceSourceMutationGuard != null
                && StatusConstants.DRAFT.equals(nextStatus)
                && !StatusConstants.DRAFT.equals(currentStatus)) {
            invoiceSourceMutationGuard.assertSalesOrderMutable(entity, "反审核");
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
    protected boolean allowRequestToWriteFinalStatus(SalesOrder entity,
                                                     SalesOrderRequest request,
                                                     Optional<String> currentStatus) {
        return currentStatus.filter(StatusConstants.DELIVERY_VERIFICATION::equals).isPresent()
                && StatusConstants.DELIVERY_VERIFICATION.equals(request.status())
                && StatusConstants.SALES_COMPLETED.equals(entity.getStatus());
    }

    @Override
    protected boolean allowProtectedStatusUpdate(SalesOrder entity, SalesOrderRequest request) {
        return protectedUpdatePolicy.allowsProtectedUpdate(entity, request);
    }

    @Override
    protected void apply(SalesOrder entity, SalesOrderRequest request) {
        lockPurchaseSources(entity, request);
        if (entity.getId() != null
                && StatusConstants.DELIVERY_VERIFICATION.equals(entity.getStatus())
                && deliveryVerificationGuard != null) {
            deliveryVerificationGuard.assertMutable(entity, "交付核定");
        } else if (entity.getId() != null && invoiceSourceMutationGuard != null) {
            invoiceSourceMutationGuard.assertSalesOrderMutable(entity, "修改");
        }
        if (salesOrderAuditedPricingService.isAuditedPricingUpdate(entity, request)) {
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
    protected SalesOrder saveUpdatedEntity(SalesOrder entity, SalesOrderRequest request) {
        if (salesOrderAuditedPricingService.isAuditedPricingUpdate(entity, request)) {
            return saveService.saveAuditedPricingUpdate(entity);
        }
        return saveEntity(entity);
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

}
