package com.leo.erp.sales.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.AbstractStatusCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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
public class SalesOrderService extends AbstractStatusCrudService<SalesOrder, SalesOrderRequest, SalesOrderResponse> {

    private static final String MODULE_KEY = "sales-order";
    private static final String[] SALES_ORDER_SEARCH_FIELDS = {"orderNo", "purchaseOrderNo", "customerName", "projectName"};

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
        super(idGenerator);
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
    public java.util.List<SalesOrderResponse> search(String keyword, int maxSize) {
        return search(keyword, SALES_ORDER_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<SalesOrderResponse> outboundImportCandidates(PageQuery query, PageFilter filter) {
        return queryService.outboundImportCandidates(query, filter);
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
    @Transactional
    public SalesOrderResponse updateStatus(Long id, String status) {
        SalesOrder order = requireEntity(id);
        String currentStatus = order.getStatus();
        SalesOrderResponse response = super.updateStatus(id, status);
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

    @Override
    protected SalesOrderResponse toDetailResponse(SalesOrder entity) {
        return queryService.toDetailResponse(entity);
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

    @Override
    protected void beforeDelete(SalesOrder entity) {
        assertOwnedByCurrentUser(entity);
        mutationGuardService.assertDeletable(entity);
    }

    @Override
    protected void afterDelete(SalesOrder entity) {
        documentChargeItemService.removeAll(MODULE_KEY, entity.getId());
        workflowService.publishDeleted(entity);
    }

    @Override
    protected void beforeStatusUpdate(SalesOrder entity, String currentStatus, String nextStatus) {
        assertOwnedByCurrentUser(entity);
        mutationGuardService.assertStatusTransitionAllowed(entity, currentStatus, nextStatus);
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
    protected boolean allowRequestToWriteFinalStatus(SalesOrder entity,
                                                     SalesOrderRequest request,
                                                     Optional<String> currentStatus) {
        return currentStatus.filter(StatusConstants.DELIVERY_VERIFICATION::equals).isPresent()
                && StatusConstants.DELIVERY_VERIFICATION.equals(request.status())
                && StatusConstants.DELIVERY_VERIFICATION.equals(entity.getStatus());
    }

    @Override
    protected boolean allowProtectedStatusUpdate(SalesOrder entity, SalesOrderRequest request) {
        return mutationGuardService.allowsProtectedUpdate(entity, request);
    }

    @Override
    protected void apply(SalesOrder entity, SalesOrderRequest request) {
        workflowService.apply(entity, request, this::nextId);
    }

    @Override
    protected SalesOrder saveEntity(SalesOrder entity) {
        return workflowService.save(entity);
    }

    @Override
    protected SalesOrder saveCreatedEntity(SalesOrder entity, SalesOrderRequest request) {
        return workflowService.saveCreated(entity, request);
    }

    @Override
    protected SalesOrder saveUpdatedEntity(SalesOrder entity, SalesOrderRequest request) {
        return workflowService.saveUpdated(entity, request);
    }

    @Override
    protected SalesOrder saveStatusEntity(SalesOrder entity) {
        return workflowService.saveStatus(entity);
    }

    @Override
    protected SalesOrderResponse toResponse(SalesOrder entity) {
        return queryService.toSummaryResponse(entity);
    }

    @Override
    protected SalesOrderResponse toSavedResponse(SalesOrder entity) {
        return toDetailResponse(entity);
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim();
    }

}
