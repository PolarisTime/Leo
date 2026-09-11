package com.leo.erp.purchase.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.service.CrudVisibilityPolicy;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderService {

    private static final CrudStatusGuard<PurchaseOrder> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderService.class);

    private final SnowflakeIdGenerator snowflakeIdGenerator;
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
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.queryService = queryService;
        this.mutationGuardService = mutationGuardService;
        this.responseAssembler = responseAssembler;
        this.supplierResolver = supplierResolver;
        this.purchaseOrderApplyService = purchaseOrderApplyService;
        this.purchaseOrderAuditPublisher = purchaseOrderAuditPublisher;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
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

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<PurchaseOrderImportCandidateResponse> inboundImportCandidates(PageQuery query, PageFilter filter) {
        return queryService.inboundImportCandidates(query, filter);
    }

    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderRequest request) {
        PurchaseOrderResponse created = createOrder(
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        purchaseOrderApplyService.syncChargeItems(created.id(), request.chargeItems());
        purchaseOrderApplyService.adjustTotalAmount(requireEntity(created.id()), BigDecimal.ZERO);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Transactional
    public PurchaseOrderResponse update(Long id, PurchaseOrderRequest request) {
        BigDecimal previousExpenseTotal = purchaseOrderApplyService.chargeTotal(id);
        PurchaseOrderResponse updated = updateOrder(id,
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        purchaseOrderApplyService.syncChargeItems(id, request.chargeItems());
        purchaseOrderApplyService.adjustTotalAmount(requireEntity(id), previousExpenseTotal);
        if (request.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
    }

    @Transactional
    public PurchaseOrderResponse updateStatus(Long id, String status) {
        PurchaseOrder purchaseOrder = requireEntity(id);
        String currentStatus = purchaseOrder.getStatus();
        PurchaseOrderResponse response = doUpdateStatus(id, status);
        if (!currentStatus.equals(response.status())) {
            publishStatusEvent(purchaseOrder, currentStatus, response.status());
        }
        return response;
    }

    @Transactional
    public void delete(Long id) {
        PurchaseOrder entity = requireEntity(id);
        STATUS_GUARD.assertDeleteAllowed(entity);
        beforeDelete(entity);
        entity.setDeletedFlag(true);
        saveEntity(entity);
        afterDelete(entity);
        log.info("{} deleted: id={}", entity.getClass().getSimpleName(), id);
    }

    /**
     * 基类 create 的显式内联：雪花 ID → 归一化 → 校验 → 应用 → 终态双写守卫 → 保存。
     */
    private PurchaseOrderResponse createOrder(PurchaseOrderRequest request) {
        PurchaseOrder entity = new PurchaseOrder();
        long entityId = snowflakeIdGenerator.nextId();
        entity.setId(entityId);
        PurchaseOrderRequest normalized = normalizeCreateRequest(request, entityId);
        validateCreate(normalized);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        PurchaseOrderResponse response = toDetailResponse(purchaseOrderRepository.saveAndFlush(entity));
        log.info("{} created: id={}", entity.getClass().getSimpleName(), entityId);
        return response;
    }

    /**
     * 基类 update 的显式内联，状态断言序列逐字保持：
     * 编辑状态守卫 → 更新校验 → 快照当前状态 → 应用请求 →
     * assertRequestStatusTransitionAllowed → allowRequestToWriteFinalStatus 分支下的
     * assertRequestDidNotWriteFinalStatus → 保存。
     */
    private PurchaseOrderResponse updateOrder(Long id, PurchaseOrderRequest request) {
        PurchaseOrder entity = requireEntity(id);
        PurchaseOrderRequest normalized = normalizeUpdateRequest(entity, request);
        STATUS_GUARD.assertEditAllowed(entity, false);
        validateUpdate(entity, normalized);
        Optional<String> currentStatus = STATUS_GUARD.resolveStatus(entity);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestStatusTransitionAllowed(entity, currentStatus, allowedStatusTransitions());
        // 基类 allowRequestToWriteFinalStatus 默认 false：普通保存一律拒绝终态写入。
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        PurchaseOrderResponse response = toDetailResponse(saveUpdatedEntity(entity, normalized));
        log.info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    /**
     * 基类 updateStatus 的显式内联：等值短路 → 迁移表校验 → beforeStatusUpdate → 写状态 → 状态保存。
     */
    private PurchaseOrderResponse doUpdateStatus(Long id, String status) {
        PurchaseOrder entity = requireEntity(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toDetailResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(allowedStatusTransitions(), currentStatus, nextStatus);
        beforeStatusUpdate(entity, currentStatus, nextStatus);
        STATUS_GUARD.writeStatus(entity, nextStatus);
        PurchaseOrderResponse response = toDetailResponse(purchaseOrderRepository.save(entity));
        log.info(
                "{} status updated: id={}, {} -> {}",
                entity.getClass().getSimpleName(),
                id,
                currentStatus,
                nextStatus
        );
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

    private PurchaseOrderResponse toDetailResponse(PurchaseOrder order) {
        return queryService.toDetailResponse(order);
    }

    private void validateCreate(PurchaseOrderRequest request) {
        if (purchaseOrderRepository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购订单号已存在");
        }
    }

    private void validateUpdate(PurchaseOrder purchaseOrder, PurchaseOrderRequest request) {
        if (!purchaseOrder.getOrderNo().equals(request.orderNo())
                && purchaseOrderRepository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购订单号已存在");
        }
    }

    private PurchaseOrderRequest normalizeCreateRequest(PurchaseOrderRequest request, long entityId) {
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

    private PurchaseOrderRequest normalizeUpdateRequest(PurchaseOrder entity, PurchaseOrderRequest request) {
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

    private void apply(PurchaseOrder purchaseOrder, PurchaseOrderRequest request) {
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

    private void beforeStatusUpdate(PurchaseOrder entity, String currentStatus, String nextStatus) {
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

    private void beforeDelete(PurchaseOrder entity) {
        mutationGuardService.assertMutable(entity, "删除");
    }

    private void afterDelete(PurchaseOrder entity) {
        purchaseOrderApplyService.removeChargeItems(entity.getId());
        publishMutationEvent(entity, "PURCHASE_ORDER_DELETED", "删除");
    }

    private PurchaseOrder saveEntity(PurchaseOrder entity) {
        return purchaseOrderRepository.save(entity);
    }

    private PurchaseOrder saveUpdatedEntity(PurchaseOrder entity, PurchaseOrderRequest request) {
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

    private PurchaseOrderResponse toResponse(PurchaseOrder entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

    private PurchaseOrder requireEntity(Long id) {
        return purchaseOrderRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    private PurchaseOrder requireDetailEntity(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    private String notFoundMessage() {
        return "采购订单不存在";
    }

    private Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.PURCHASE_ORDER_TRANSITIONS;
    }

    private Page<PurchaseOrder> pageEntities(PageQuery query,
                                             Specification<PurchaseOrder> specification,
                                             PurchaseOrderRepository repository) {
        Specification<PurchaseOrder> effectiveSpec =
                VISIBILITY_POLICY.applyDeletedVisibility(specification, allowViewingDeletedRecords());
        return repository.findAll(effectiveSpec, query.toPageable("id"));
    }

    private boolean allowViewingDeletedRecords() {
        return true;
    }

    private long nextId() {
        return snowflakeIdGenerator.nextId();
    }

    private String resolveCreateBusinessNo(Long entityId) {
        if (entityId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "业务单据雪花ID尚未分配");
        }
        return String.valueOf(entityId);
    }
}
