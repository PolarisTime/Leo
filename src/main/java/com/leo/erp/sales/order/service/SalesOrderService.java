package com.leo.erp.sales.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SalesOrderService extends AbstractCrudService<SalesOrder, SalesOrderRequest, SalesOrderResponse> {

    private final SalesOrderRepository repository;
    private final SalesOrderResponseAssembler responseAssembler;
    private final SalesOrderApplyService salesOrderApplyService;
    private final SalesOrderPurchaseAllocationService salesOrderPurchaseAllocationService;
    private final SalesOrderAuditedPricingService salesOrderAuditedPricingService;
    private final SalesOrderProtectedUpdatePolicy protectedUpdatePolicy;
    private final SalesOrderSaveService saveService;

    @Autowired
    public SalesOrderService(SalesOrderRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             SalesOrderResponseAssembler responseAssembler,
                             SalesOrderApplyService salesOrderApplyService,
                             SalesOrderPurchaseAllocationService salesOrderPurchaseAllocationService,
                             SalesOrderAuditedPricingService salesOrderAuditedPricingService,
                             SalesOrderProtectedUpdatePolicy protectedUpdatePolicy,
                             SalesOrderSaveService saveService) {
        super(idGenerator);
        this.repository = repository;
        this.responseAssembler = responseAssembler;
        this.salesOrderApplyService = salesOrderApplyService;
        this.salesOrderPurchaseAllocationService = salesOrderPurchaseAllocationService;
        this.salesOrderAuditedPricingService = salesOrderAuditedPricingService;
        this.protectedUpdatePolicy = protectedUpdatePolicy;
        this.saveService = saveService;
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
                request.deliveryDate(),
                request.salesName(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected void beforeDelete(SalesOrder entity) {
        salesOrderPurchaseAllocationService.releaseSalesOrderItems(entity);
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
        return StatusConstants.DRAFT_AUDIT_TRANSITIONS;
    }

    @Override
    protected boolean allowProtectedStatusUpdate(SalesOrder entity, SalesOrderRequest request) {
        return protectedUpdatePolicy.allowsProtectedUpdate(entity, request);
    }

    @Override
    protected void apply(SalesOrder entity, SalesOrderRequest request) {
        if (salesOrderAuditedPricingService.isAuditedPricingUpdate(entity, request)) {
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
