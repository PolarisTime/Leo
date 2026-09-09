package com.leo.erp.sales.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.service.CrudVisibilityPolicy;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class SalesOrderService {

    private static final String MODULE_KEY = "sales-order";
    private static final String[] SALES_ORDER_SEARCH_FIELDS = {"orderNo", "purchaseOrderNo", "customerName", "projectName"};
    private static final CrudStatusGuard<SalesOrder> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Logger log = LoggerFactory.getLogger(SalesOrderService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final SalesOrderRepository repository;
    private final DocumentChargeItemService documentChargeItemService;
    private final SalesOrderQueryService queryService;
    private final SalesOrderMutationGuardService mutationGuardService;
    private final SalesOrderWorkflowService workflowService;

    @Autowired
    public SalesOrderService(SalesOrderRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             DocumentChargeItemService documentChargeItemService,
                             SalesOrderQueryService queryService,
                             SalesOrderMutationGuardService mutationGuardService,
                             SalesOrderWorkflowService workflowService) {
        this.idGenerator = idGenerator;
        this.repository = repository;
        this.documentChargeItemService = documentChargeItemService;
        this.queryService = queryService;
        this.mutationGuardService = mutationGuardService;
        this.workflowService = workflowService;
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> page(PageQuery query, PageFilter filter, String productKeyword) {
        return page(query, filter, productKeyword, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> page(PageQuery query, PageFilter filter, String productKeyword, Boolean pendingOnly) {
        return page(query, filter, productKeyword, pendingOnly, null, null);
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> page(PageQuery query, PageFilter filter, String productKeyword, Boolean pendingOnly, Boolean referenced) {
        return page(query, filter, productKeyword, pendingOnly, referenced, null);
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> page(PageQuery query,
                                         PageFilter filter,
                                         String productKeyword,
                                         Boolean pendingOnly,
                                         Boolean referenced,
                                         String referencedBy) {
        return queryService.page(query, filter, productKeyword, pendingOnly, referenced, referencedBy);
    }

    @Transactional(readOnly = true)
    public List<SalesOrderResponse> search(String keyword, int maxSize) {
        Specification<SalesOrder> spec = combineSpecifications(
                VISIBILITY_POLICY.applyDeletedVisibility(null, false),
                Specs.keywordLike(keyword, SALES_ORDER_SEARCH_FIELDS)
        );
        return repository.findAll(spec, PageRequest.of(0, maxSize))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<SalesOrderResponse> outboundImportCandidates(PageQuery query, PageFilter filter) {
        return queryService.outboundImportCandidates(query, filter);
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderRequest request) {
        SalesOrderResponse created = createOrder(
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        applyChargeTotal(created.id(), BigDecimal.ZERO);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderRequest request) {
        BigDecimal previousExpenseTotal = documentChargeItemService
                .sumAmount(documentChargeItemService.list(MODULE_KEY, id));
        SalesOrderResponse updated = updateOrder(id,
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
        updateOrder(id, withStatus(request, StatusConstants.DELIVERY_VERIFICATION));
        return completeSalesOrder(id);
    }

    @Transactional
    public SalesOrderResponse updateStatus(Long id, String status) {
        SalesOrder order = requireEntity(id);
        String currentStatus = order.getStatus();
        SalesOrderResponse response = doUpdateStatus(id, status);
        if (!Objects.equals(currentStatus, response.status())) {
            workflowService.publishStatusChanged(order, currentStatus, response.status());
        }
        return response;
    }

    @Transactional
    public SalesOrderResponse completeSalesOrder(Long id) {
        SalesOrder order = repository.findForUpdateByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        assertOwnedByCurrentUser(order);
        return workflowService.completeSalesOrder(order);
    }

    @Transactional
    public void delete(Long id) {
        SalesOrder entity = requireEntity(id);
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
    private SalesOrderResponse createOrder(SalesOrderRequest request) {
        SalesOrder entity = newEntity();
        long entityId = idGenerator.nextId();
        assignId(entity, entityId);
        SalesOrderRequest normalized = normalizeCreateRequest(request, entityId);
        validateCreate(normalized);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        SalesOrderResponse response = toSavedResponse(saveCreatedEntity(entity, normalized));
        log.info("{} created: id={}", entity.getClass().getSimpleName(), entityId);
        return response;
    }

    /**
     * 基类 update 的显式内联，状态断言序列逐字保持：
     * 编辑状态守卫 → 更新校验 → 快照当前状态 → 应用请求 →
     * assertRequestStatusTransitionAllowed → allowRequestToWriteFinalStatus 分支下的
     * assertRequestDidNotWriteFinalStatus → 保存。
     */
    private SalesOrderResponse updateOrder(Long id, SalesOrderRequest request) {
        SalesOrder entity = requireEntity(id);
        SalesOrderRequest normalized = normalizeUpdateRequest(entity, request);
        STATUS_GUARD.assertEditAllowed(entity, allowProtectedStatusUpdate(entity, normalized));
        validateUpdate(entity, normalized);
        Optional<String> currentStatus = STATUS_GUARD.resolveStatus(entity);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestStatusTransitionAllowed(entity, currentStatus, allowedStatusTransitions());
        if (!allowRequestToWriteFinalStatus(entity, normalized, currentStatus)) {
            STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        }
        SalesOrderResponse response = toSavedResponse(saveUpdatedEntity(entity, normalized));
        log.info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    /**
     * 基类 updateStatus 的显式内联：等值短路 → 迁移表校验 → beforeStatusUpdate → 写状态 → 状态保存。
     */
    private SalesOrderResponse doUpdateStatus(Long id, String status) {
        SalesOrder entity = requireEntity(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toSavedResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(allowedStatusTransitions(), currentStatus, nextStatus);
        beforeStatusUpdate(entity, currentStatus, nextStatus);
        STATUS_GUARD.writeStatus(entity, nextStatus);
        SalesOrderResponse response = toSavedResponse(saveStatusEntity(entity));
        log.info(
                "{} status updated: id={}, {} -> {}",
                entity.getClass().getSimpleName(),
                id,
                currentStatus,
                nextStatus
        );
        return response;
    }

    protected void validateCreate(SalesOrderRequest request) {
        if (repository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售订单号已存在");
        }
        String requestedStatus = normalizeStatus(request.status());
        if (!requestedStatus.isEmpty() && !StatusConstants.DRAFT.equals(requestedStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新销售订单只能保存为草稿，审核必须通过状态操作完成");
        }
    }

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

    private SalesOrderRequest normalizeCreateRequest(SalesOrderRequest request, long entityId) {
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
    private void applyChargeTotal(Long orderId, BigDecimal previousExpenseTotal) {
        SalesOrder order = requireEntity(orderId);
        BigDecimal currentExpense = documentChargeItemService
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

    private SalesOrderRequest normalizeUpdateRequest(SalesOrder entity, SalesOrderRequest request) {
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

    protected void beforeDelete(SalesOrder entity) {
        assertOwnedByCurrentUser(entity);
        mutationGuardService.assertDeletable(entity);
    }

    protected void afterDelete(SalesOrder entity) {
        documentChargeItemService.removeAll(MODULE_KEY, entity.getId());
        workflowService.publishDeleted(entity);
    }

    protected void beforeStatusUpdate(SalesOrder entity, String currentStatus, String nextStatus) {
        assertOwnedByCurrentUser(entity);
        mutationGuardService.assertStatusTransitionAllowed(entity, currentStatus, nextStatus);
    }

    private SalesOrder newEntity() {
        SalesOrder order = new SalesOrder();
        order.setOwnerUserId(requireCurrentUserId());
        return order;
    }

    private void assignId(SalesOrder entity, Long id) {
        entity.setId(id);
    }

    private Optional<SalesOrder> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    private Optional<SalesOrder> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    private String notFoundMessage() {
        return "销售订单不存在";
    }

    private boolean allowViewingDeletedRecords() {
        return true;
    }

    private Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.SALES_ORDER_TRANSITIONS;
    }

    protected boolean allowRequestToWriteFinalStatus(SalesOrder entity,
                                                     SalesOrderRequest request,
                                                     Optional<String> currentStatus) {
        return currentStatus.filter(StatusConstants.DELIVERY_VERIFICATION::equals).isPresent()
                && StatusConstants.DELIVERY_VERIFICATION.equals(request.status())
                && StatusConstants.DELIVERY_VERIFICATION.equals(entity.getStatus());
    }

    private boolean allowProtectedStatusUpdate(SalesOrder entity, SalesOrderRequest request) {
        return mutationGuardService.allowsProtectedUpdate(entity, request);
    }

    protected void apply(SalesOrder entity, SalesOrderRequest request) {
        workflowService.apply(entity, request, this::nextId);
    }

    private SalesOrder saveEntity(SalesOrder entity) {
        return workflowService.save(entity);
    }

    protected SalesOrder saveCreatedEntity(SalesOrder entity, SalesOrderRequest request) {
        return workflowService.saveCreated(entity, request);
    }

    protected SalesOrder saveUpdatedEntity(SalesOrder entity, SalesOrderRequest request) {
        return workflowService.saveUpdated(entity, request);
    }

    protected SalesOrder saveStatusEntity(SalesOrder entity) {
        return workflowService.saveStatus(entity);
    }

    private SalesOrderResponse toResponse(SalesOrder entity) {
        return queryService.toSummaryResponse(entity);
    }

    private SalesOrderResponse toDetailResponse(SalesOrder entity) {
        return queryService.toDetailResponse(entity);
    }

    private SalesOrderResponse toSavedResponse(SalesOrder entity) {
        return toDetailResponse(entity);
    }

    private SalesOrder requireEntity(Long id) {
        return findActiveEntity(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    private SalesOrder requireDetailEntity(Long id) {
        if (allowViewingDeletedRecords()) {
            return findVisibleEntity(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        }
        return requireEntity(id);
    }

    private long nextId() {
        return idGenerator.nextId();
    }

    private String resolveCreateBusinessNo(Long entityId) {
        if (entityId == null || entityId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "业务单据雪花ID尚未分配");
        }
        return String.valueOf(entityId);
    }

    private Specification<SalesOrder> combineSpecifications(Specification<SalesOrder> left,
                                                            Specification<SalesOrder> right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.and(right);
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim();
    }

}
