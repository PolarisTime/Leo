package com.leo.erp.sales.order.service;

import com.leo.erp.security.permission.StatusTransitionPermissionGuard;
import com.leo.erp.common.support.ValidationMessages;
import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.common.transaction.OptimisticLockRetryExecutor;
import com.leo.erp.sales.order.config.SalesOrderRetryProperties;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.security.permission.PermissionChecker;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.support.SecurityPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class SalesOrderService {

    private static final String MODULE_KEY = ModuleKeys.SALES_ORDER;
    private static final CrudStatusGuard<SalesOrder> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();
    private static final Logger log = LoggerFactory.getLogger(SalesOrderService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final SalesOrderRepository repository;
    private final DocumentChargeItemService documentChargeItemService;
    private final SalesOrderQueryService queryService;
    private final SalesOrderMutationGuardService mutationGuardService;
    private final SalesOrderWorkflowService workflowService;
    private final PermissionChecker permissionChecker;
    private final SalesOrderPriceRuleService priceRuleService;
    /** 同一单据并发写的有界乐观锁重试；配置来自 {@code leo.sales-order.retry.*}，启动期固定。 */
    private final OptimisticLockRetryExecutor optimisticLockRetry;

    @Autowired
    public SalesOrderService(SalesOrderRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             DocumentChargeItemService documentChargeItemService,
                             SalesOrderQueryService queryService,
                             SalesOrderMutationGuardService mutationGuardService,
                             SalesOrderWorkflowService workflowService,
                             PermissionChecker permissionChecker,
                             SalesOrderPriceRuleService priceRuleService,
                             TransactionTemplate transactionTemplate,
                             SalesOrderRetryProperties retryProperties) {
        this.idGenerator = idGenerator;
        this.repository = repository;
        this.documentChargeItemService = documentChargeItemService;
        this.queryService = queryService;
        this.mutationGuardService = mutationGuardService;
        this.workflowService = workflowService;
        this.permissionChecker = permissionChecker;
        this.priceRuleService = priceRuleService;
        this.optimisticLockRetry = new OptimisticLockRetryExecutor(
                transactionTemplate,
                retryProperties.getMaxAttempts(),
                Duration.ofMillis(retryProperties.getBackoffMillis()));
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

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<SalesOrderResponse> outboundImportCandidates(PageQuery query, PageFilter filter) {
        return queryService.outboundImportCandidates(query, filter);
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    /**
     * 创建单据。未纳入乐观锁重试：新实体走 INSERT，冲突形态是订单号唯一键冲突
     * （{@code DataIntegrityViolationException}），按约定唯一键冲突禁止重试；
     * 内部审核分支自调用 {@link #updateStatus} 时执行器检测到本方法的活动事务，
     * 直接参与而不新开事务，与改造前的自调用语义一致。
     */
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

    /**
     * 整体替换写路径（{@code PUT /api/v2.0/sales-orders/{id}}），带服务端有界乐观锁重试。
     *
     * <p><b>为什么不再声明 {@code @Transactional}：</b>事务改由 {@link OptimisticLockRetryExecutor}
     * 通过 {@link TransactionTemplate} 为「每次尝试」单独开启——只有把事务边界放进重试循环内部，
     * 冲突后的重试才能拿到干净的新事务、重新加载实体并重放整段写逻辑（费用同步、合计重算、审核分支）；
     * 若保留 {@code @Transactional}，事务会在进入重试循环之前开启，重试无法形成独立事务，
     * 且 commit 阶段的冲突也脱离重试循环。方法体本身仍在单个事务内原子执行，语义与改造前一致。
     *
     * <p><b>自调用语义：</b>{@code request.audit()} 分支内部自调用 {@link #updateStatus} 时，
     * 执行器检测到活动事务会直接执行原逻辑（不新开事务、不重试），与改造前「自调用参与外层事务」完全一致。
     *
     * <p><b>副作用：</b>费用同步、合计修正、状态发布全部发生在同一事务内，失败整体回滚；
     * 领域事件由 {@code @ApplicationModuleListener}（AFTER_COMMIT）在提交成功后投递，
     * 失败的尝试不会重复发布事件/日志。重试仅识别乐观锁异常，耗尽后原样抛出 → 409（现状不变）。
     */
    public SalesOrderResponse update(Long id, SalesOrderRequest request) {
        return optimisticLockRetry.execute("sales-order.update", () -> doUpdate(id, request));
    }

    /**
     * {@link #update} 的整段写逻辑（在执行器开启的事务内运行，可被完整重放）。
     */
    private SalesOrderResponse doUpdate(Long id, SalesOrderRequest request) {
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

    /**
     * 保存并确认交付核定（{@code POST /api/v2.0/sales-orders/{id}/delivery-verifications}）。
     *
     * <p>与 PUT 同属「整体替换同一单据」的写路径，冲突画像一致，故同样纳入有界乐观锁重试：
     * 每次尝试在新事务中重新加载单据、重放替换 + 价格规定 + 完成销售的整段逻辑，
     * 任一环节失败即整体回滚，不会留下半张单据或重复的完成事件。
     */
    public SalesOrderResponse updateAndComplete(Long id, SalesOrderRequest request) {
        return optimisticLockRetry.execute("sales-order.updateAndComplete",
                () -> doUpdateAndComplete(id, request));
    }

    /**
     * {@link #updateAndComplete} 的整段写逻辑（在执行器开启的事务内运行，可被完整重放）。
     */
    private SalesOrderResponse doUpdateAndComplete(Long id, SalesOrderRequest request) {
        updateOrder(id, withStatus(request, StatusConstants.DELIVERY_VERIFICATION));
        // 应用交付核定所选价格规定: 校验归属、快照到单据、记录项目上次使用。
        SalesOrder order = requireEntity(id);
        priceRuleService.applyPriceRule(order, request.priceRuleId());
        repository.save(order);
        return completeSalesOrder(id);
    }

    /**
     * 状态迁移（{@code PATCH /api/v2.0/sales-orders/{id}/status}），带服务端有界乐观锁重试。
     *
     * <p><b>为何纳入：</b>并发审核/反审核打在同一单据上时同样会触发 {@code @Version} 冲突，
     * 且状态写是「读当前状态 → 校验迁移表 → 写入」的整段逻辑，冲突后在新事务中重放是安全的。
     *
     * <p><b>自调用语义：</b>{@link #create} 与 {@link #doUpdate} 的审核分支都会自调用本方法；
     * 此时已存在活动事务，执行器直接执行原逻辑——不新开事务、不重试，参与外层事务的行为与
     * 改造前（{@code @Transactional} 被自调用绕过、直接加入外层事务）逐字一致。
     *
     * <p><b>事件不重复：</b>{@code publishStatusChanged} 走的领域事件是
     * {@code @ApplicationModuleListener}（AFTER_COMMIT + REQUIRES_NEW）投递：失败尝试随事务回滚、
     * 事件被丢弃，只有成功提交的那次尝试投递一次，重试不会造成重复状态变更日志。
     */
    public SalesOrderResponse updateStatus(Long id, String status) {
        return optimisticLockRetry.execute("sales-order.updateStatus",
                () -> doUpdateStatusWithPublish(id, status));
    }

    /**
     * {@link #updateStatus} 的整段写逻辑（在执行器开启的事务内运行，可被完整重放）。
     */
    private SalesOrderResponse doUpdateStatusWithPublish(Long id, String status) {
        SalesOrder order = requireEntity(id);
        String currentStatus = order.getStatus();
        SalesOrderResponse response = doUpdateStatus(id, status);
        if (!Objects.equals(currentStatus, response.status())) {
            workflowService.publishStatusChanged(order, currentStatus, response.status());
        }
        return response;
    }

    /**
     * 完成销售（专用子资源端点）。暂未纳入乐观锁重试：与「整体替换同一单据」并发的主冲突面
     * 已由 {@link #updateAndComplete} 的重试边界覆盖；本方法如需接入，按同一模式改造
     * （去掉 {@code @Transactional}、改由执行器包裹整段逻辑）即可，语义安全性同样成立。
     */
    @Transactional
    public SalesOrderResponse completeSalesOrder(Long id) {
        SalesOrder order = repository.findForUpdateByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        assertOwnedByCurrentUser(order);
        return workflowService.completeSalesOrder(order);
    }

    /**
     * 软删除单据。未纳入乐观锁重试：删除端点不在压测暴露的冲突面内（整体替换才是热点），
     * 且删除后的重复重放需要额外评估幂等性；接入方式与完成销售一致。
     */
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
        assertUnitPriceChangeAllowed(entity, normalized);
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
     * 字段级写权限校验：无 {@code sales-orders:update:unit-price} 时，禁止在更新中改动
     * 既有明细的单价；新增行（无 id）不在本次校验内（其定价受 create 权限约束）。
     */
    private void assertUnitPriceChangeAllowed(SalesOrder entity, SalesOrderRequest request) {
        if (permissionChecker.has(PermissionCodes.SALES_ORDERS_UPDATE_UNIT_PRICE)) {
            return;
        }
        if (request.items() == null || entity.getItems() == null) {
            return;
        }
        Map<Long, BigDecimal> existingPriceById = new HashMap<>();
        for (SalesOrderItem item : entity.getItems()) {
            if (item.getId() != null) {
                existingPriceById.put(item.getId(), item.getUnitPrice());
            }
        }
        for (SalesOrderItemRequest item : request.items()) {
            if (item == null || item.id() == null) {
                continue;
            }
            BigDecimal previous = existingPriceById.get(item.id());
            if (previous == null) {
                continue;
            }
            BigDecimal next = item.unitPrice() == null ? BigDecimal.ZERO : item.unitPrice();
            if (previous.compareTo(next) != 0) {
                throw new AccessDeniedException(
                        "缺少权限: " + PermissionCodes.SALES_ORDERS_UPDATE_UNIT_PRICE);
            }
        }
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
        StatusTransitionPermissionGuard.requireForTransition(MODULE_KEY, currentStatus, nextStatus);
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
                request.priceRuleId(),
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
                request.priceRuleId(),
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
                request.priceRuleId(),
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
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.SNOWFLAKE_ID_NOT_ASSIGNED);
        }
        return String.valueOf(entityId);
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim();
    }

}
