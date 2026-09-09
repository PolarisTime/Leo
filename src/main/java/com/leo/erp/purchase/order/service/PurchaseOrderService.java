package com.leo.erp.purchase.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.AbstractStatusCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.purchase.order.audit.PurchaseOrderAuditPublisher;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderReferenceQueryRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderImportCandidateResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderService extends AbstractStatusCrudService<
        PurchaseOrder, PurchaseOrderRequest, PurchaseOrderResponse> {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderQueryService queryService;
    private final PurchaseOrderMutationGuardService mutationGuardService;
    private final PurchaseOrderResponseAssembler responseAssembler;
    private final PurchaseOrderSupplierResolver supplierResolver;
    private final PurchaseOrderApplyService purchaseOrderApplyService;
    private final PurchaseOrderAuditPublisher purchaseOrderAuditPublisher;

    @Autowired
    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                SnowflakeIdGenerator snowflakeIdGenerator,
                                PurchaseOrderQueryService queryService,
                                PurchaseOrderMutationGuardService mutationGuardService,
                                PurchaseOrderResponseAssembler responseAssembler,
                                PurchaseOrderSupplierResolver supplierResolver,
                                PurchaseOrderApplyService purchaseOrderApplyService,
                                PurchaseOrderAuditPublisher purchaseOrderAuditPublisher) {
        super(snowflakeIdGenerator);
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.queryService = queryService;
        this.mutationGuardService = mutationGuardService;
        this.responseAssembler = responseAssembler;
        this.supplierResolver = supplierResolver;
        this.purchaseOrderApplyService = purchaseOrderApplyService;
        this.purchaseOrderAuditPublisher = purchaseOrderAuditPublisher;
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> page(PageQuery query, PageFilter filter) {
        return page(query, filter, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> page(PageQuery query, PageFilter filter, Boolean pendingOnly) {
        return page(query, filter, pendingOnly, null, null);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> page(PageQuery query, PageFilter filter, Boolean pendingOnly, Boolean referenced) {
        return page(query, filter, pendingOnly, referenced, null);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> page(PageQuery query,
                                            PageFilter filter,
                                            Boolean pendingOnly,
                                            Boolean referenced,
                                            String referencedBy) {
        Page<PurchaseOrder> entities;
        if (referenced != null || referencedBy != null) {
            entities = queryService.findByReferenceFilter(query, filter, pendingOnly, referenced, referencedBy);
        } else if (Boolean.TRUE.equals(pendingOnly)) {
            entities = queryService.findPending(query, filter);
        } else {
            entities = pageEntities(query, queryService.summarySpecification(filter), purchaseOrderRepository);
        }
        Map<Long, PurchaseOrderReferenceQueryRepository.ReferenceStatus> statuses =
                queryService.findReferenceStatusByOrderIds(
                        entities.getContent().stream().map(PurchaseOrder::getId).toList());
        return entities.map(order -> queryService.applyReferenceFlags(
                toResponse(order),
                statuses.get(order.getId())
        ));
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> search(String keyword, int maxSize) {
        return search(keyword, PurchaseOrderQueryService.PURCHASE_ORDER_SEARCH_FIELDS, maxSize,
                null, purchaseOrderRepository);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<PurchaseOrderImportCandidateResponse> inboundImportCandidates(PageQuery query, PageFilter filter) {
        return queryService.inboundImportCandidates(query, filter);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderRequest request) {
        PurchaseOrderResponse created = super.create(
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        purchaseOrderApplyService.syncChargeItems(created.id(), request.chargeItems());
        purchaseOrderApplyService.adjustTotalAmount(requireEntity(created.id()), BigDecimal.ZERO);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Override
    @Transactional
    public PurchaseOrderResponse update(Long id, PurchaseOrderRequest request) {
        BigDecimal previousExpenseTotal = purchaseOrderApplyService.chargeTotal(id);
        PurchaseOrderResponse updated = super.update(id,
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        purchaseOrderApplyService.syncChargeItems(id, request.chargeItems());
        purchaseOrderApplyService.adjustTotalAmount(requireEntity(id), previousExpenseTotal);
        if (request.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
    }

    @Override
    protected PurchaseOrderResponse toDetailResponse(PurchaseOrder order) {
        return queryService.toDetailResponse(order);
    }

    @Override
    protected void validateCreate(PurchaseOrderRequest request) {
        if (purchaseOrderRepository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购订单号已存在");
        }
    }

    @Override
    protected void validateUpdate(PurchaseOrder purchaseOrder, PurchaseOrderRequest request) {
        if (!purchaseOrder.getOrderNo().equals(request.orderNo())
                && purchaseOrderRepository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购订单号已存在");
        }
    }

    @Override
    protected PurchaseOrderRequest normalizeCreateRequest(PurchaseOrderRequest request, long entityId) {
        return new PurchaseOrderRequest(
                resolveCreateBusinessNo(entityId),
                request.supplierId(),
                request.supplierCode(),
                request.supplierName(),
                request.orderDate(),
                request.buyerName(),
                request.settlementCompanyId(),
                request.status(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    private PurchaseOrderRequest withStatus(PurchaseOrderRequest request, String status) {
        return new PurchaseOrderRequest(
                request.orderNo(),
                request.supplierId(),
                request.supplierCode(),
                request.supplierName(),
                request.orderDate(),
                request.buyerName(),
                request.settlementCompanyId(),
                status,
                request.remark(),
                request.items(),
                request.chargeItems(),
                request.audit()
        );
    }

    @Override
    protected PurchaseOrderRequest normalizeUpdateRequest(PurchaseOrder entity, PurchaseOrderRequest request) {
        return new PurchaseOrderRequest(
                entity.getOrderNo(),
                request.supplierId() == null ? entity.getSupplierId() : request.supplierId(),
                request.supplierCode() == null || request.supplierCode().isBlank()
                        ? entity.getSupplierCode()
                        : request.supplierCode(),
                request.supplierName(),
                request.orderDate(),
                request.buyerName(),
                request.settlementCompanyId(),
                request.status(),
                request.remark(),
                request.items(),
                request.chargeItems(),
                request.audit()
        );
    }

    @Override
    protected PurchaseOrder newEntity() {
        return new PurchaseOrder();
    }

    @Override
    protected void assignId(PurchaseOrder entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<PurchaseOrder> findActiveEntity(Long id) {
        return purchaseOrderRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<PurchaseOrder> findVisibleEntity(Long id) {
        return purchaseOrderRepository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "采购订单不存在";
    }

    @Override
    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    @Override
    protected Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.PURCHASE_ORDER_TRANSITIONS;
    }

    @Override
    @Transactional
    public PurchaseOrderResponse updateStatus(Long id, String status) {
        PurchaseOrder purchaseOrder = requireEntity(id);
        String currentStatus = purchaseOrder.getStatus();
        PurchaseOrderResponse response = super.updateStatus(id, status);
        if (!currentStatus.equals(response.status())) {
            publishStatusEvent(purchaseOrder, currentStatus, response.status());
        }
        return response;
    }

    private void publishStatusEvent(PurchaseOrder purchaseOrder, String currentStatus, String nextStatus) {
        String eventType;
        String actionType;
        if (StatusConstants.DRAFT.equals(currentStatus) && StatusConstants.AUDITED.equals(nextStatus)) {
            eventType = "PURCHASE_ORDER_AUDITED";
            actionType = "审核";
        } else if (StatusConstants.AUDITED.equals(currentStatus) && StatusConstants.DRAFT.equals(nextStatus)) {
            eventType = "PURCHASE_ORDER_REVERSE_AUDITED";
            actionType = "反审核";
        } else {
            return;
        }

        purchaseOrderAuditPublisher.publish(
                purchaseOrder,
                eventType,
                actionType,
                "采购订单状态 " + currentStatus + " -> " + nextStatus
        );
    }

    @Override
    protected void apply(PurchaseOrder purchaseOrder, PurchaseOrderRequest request) {
        PurchaseOrderSaveValidations.assertLineQuantities(request);
        mutationGuardService.assertUpdateAllowed(purchaseOrder, request.items());
        PurchaseOrderSaveValidations.assertSettlementCompanyMutable(purchaseOrder, request.settlementCompanyId());
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                purchaseOrder.getStatus() != null ? purchaseOrder.getStatus() : StatusConstants.DRAFT,
                "采购订单状态",
                StatusConstants.ALLOWED_PURCHASE_ORDER_STATUS
        );
        PurchaseOrderSaveValidations.assertStatusNotChangedBySave(purchaseOrder, nextStatus);
        purchaseOrder.setOrderNo(request.orderNo());
        PurchaseOrderSupplierResolver.SupplierIdentity supplierIdentity =
                supplierResolver.requireMasterSupplier(
                        request.supplierId(),
                        request.supplierCode(),
                        request.supplierName()
                );
        purchaseOrder.setSupplierId(supplierIdentity.supplierId());
        purchaseOrder.setSupplierCode(supplierIdentity.supplierCode());
        purchaseOrder.setSupplierName(supplierIdentity.supplierName());
        purchaseOrder.setOrderDate(request.orderDate());
        purchaseOrder.setBuyerName(request.buyerName());
        PurchaseOrderSupplierResolver.SettlementCompanySnapshot settlementCompany =
                supplierResolver.requireSettlementCompany(request.settlementCompanyId());
        purchaseOrder.setSettlementCompanyId(settlementCompany.id());
        purchaseOrder.setSettlementCompanyName(settlementCompany.name());
        purchaseOrder.setStatus(nextStatus);
        purchaseOrder.setRemark(request.remark());
        purchaseOrderApplyService.applyItems(purchaseOrder, request, this::nextId);
    }

    @Override
    protected void beforeStatusUpdate(PurchaseOrder entity, String currentStatus, String nextStatus) {
        if (StatusConstants.DRAFT.equals(currentStatus) && StatusConstants.AUDITED.equals(nextStatus)) {
            PurchaseOrderSaveValidations.assertAuditableLineQuantities(entity);
        }
        if (StatusConstants.PURCHASE_COMPLETED.equals(nextStatus)
                && !StatusConstants.PURCHASE_COMPLETED.equals(currentStatus)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "完成采购状态由采购入库审核自动触发"
            );
        }
        if (StatusConstants.PURCHASE_COMPLETED.equals(currentStatus)
                && StatusConstants.AUDITED.equals(nextStatus)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "完成采购状态只能由采购入库反审核自动回退"
            );
        }
        if (StatusConstants.DRAFT.equals(nextStatus)
                && !StatusConstants.DRAFT.equals(currentStatus)) {
            mutationGuardService.assertMutable(entity, "反审核");
        }
    }

    @Override
    protected void beforeDelete(PurchaseOrder entity) {
        mutationGuardService.assertMutable(entity, "删除");
    }

    @Override
    protected void afterDelete(PurchaseOrder entity) {
        purchaseOrderApplyService.removeChargeItems(entity.getId());
        publishMutationEvent(entity, "PURCHASE_ORDER_DELETED", "删除");
    }

    @Override
    protected PurchaseOrder saveEntity(PurchaseOrder entity) {
        return purchaseOrderRepository.save(entity);
    }

    @Override
    protected PurchaseOrder saveCreatedEntity(PurchaseOrder entity, PurchaseOrderRequest request) {
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(entity);
        publishMutationEvent(saved, "PURCHASE_ORDER_CREATED", "新增");
        return saved;
    }

    @Override
    protected PurchaseOrder saveUpdatedEntity(PurchaseOrder entity, PurchaseOrderRequest request) {
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(entity);
        publishMutationEvent(saved, "PURCHASE_ORDER_UPDATED", "编辑");
        return saved;
    }

    private void publishMutationEvent(PurchaseOrder entity, String eventType, String actionType) {
        purchaseOrderAuditPublisher.publish(
                entity,
                eventType,
                actionType,
                actionType + "采购订单 " + entity.getOrderNo()
        );
    }

    @Override
    protected PurchaseOrderResponse toResponse(PurchaseOrder entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

    @Override
    protected PurchaseOrderResponse toSavedResponse(PurchaseOrder entity) {
        return toDetailResponse(entity);
    }
}
