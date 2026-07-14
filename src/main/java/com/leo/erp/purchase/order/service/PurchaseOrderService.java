package com.leo.erp.purchase.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.common.service.SupplierLedgerLockService;
import com.leo.erp.finance.payment.service.PaymentPurchasePrepaymentService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderImportCandidateResponse;
import com.leo.erp.purchase.order.web.dto.PieceWeightResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderService extends AbstractCrudService<PurchaseOrder, PurchaseOrderRequest, PurchaseOrderResponse> {

    private static final Set<String> PREPAYMENT_SOURCE_STATUSES = Set.of(
            StatusConstants.AUDITED,
            StatusConstants.PURCHASE_COMPLETED
    );

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderAvailabilityService availabilityService;
    private final PurchaseOrderResponseAssembler responseAssembler;
    private final PurchaseOrderSupplierResolver supplierResolver;
    private final PurchaseOrderApplyService purchaseOrderApplyService;
    private final PurchaseOrderPieceWeightQueryService pieceWeightQueryService;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final CompanySettingService companySettingService;
    private final PaymentPurchasePrepaymentService purchasePrepaymentService;
    private final PurchaseOrderDownstreamMutationGuard downstreamMutationGuard;
    private SupplierLedgerLockService supplierLedgerLockService;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                SnowflakeIdGenerator snowflakeIdGenerator,
                                PurchaseOrderAvailabilityService availabilityService,
                                PurchaseOrderResponseAssembler responseAssembler,
                                PurchaseOrderSupplierResolver supplierResolver,
                                PurchaseOrderApplyService purchaseOrderApplyService,
                                PurchaseOrderPieceWeightQueryService pieceWeightQueryService,
                                WorkflowTransitionGuard workflowTransitionGuard,
                                CompanySettingService companySettingService) {
        this(
                purchaseOrderRepository,
                snowflakeIdGenerator,
                availabilityService,
                responseAssembler,
                supplierResolver,
                purchaseOrderApplyService,
                pieceWeightQueryService,
                workflowTransitionGuard,
                companySettingService,
                null
        );
    }

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                SnowflakeIdGenerator snowflakeIdGenerator,
                                PurchaseOrderAvailabilityService availabilityService,
                                PurchaseOrderResponseAssembler responseAssembler,
                                PurchaseOrderSupplierResolver supplierResolver,
                                PurchaseOrderApplyService purchaseOrderApplyService,
                                PurchaseOrderPieceWeightQueryService pieceWeightQueryService,
                                WorkflowTransitionGuard workflowTransitionGuard,
                                CompanySettingService companySettingService,
                                PaymentPurchasePrepaymentService purchasePrepaymentService) {
        this(
                purchaseOrderRepository,
                snowflakeIdGenerator,
                availabilityService,
                responseAssembler,
                supplierResolver,
                purchaseOrderApplyService,
                pieceWeightQueryService,
                workflowTransitionGuard,
                companySettingService,
                purchasePrepaymentService,
                null
        );
    }

    @Autowired
    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                SnowflakeIdGenerator snowflakeIdGenerator,
                                PurchaseOrderAvailabilityService availabilityService,
                                PurchaseOrderResponseAssembler responseAssembler,
                                PurchaseOrderSupplierResolver supplierResolver,
                                PurchaseOrderApplyService purchaseOrderApplyService,
                                PurchaseOrderPieceWeightQueryService pieceWeightQueryService,
                                WorkflowTransitionGuard workflowTransitionGuard,
                                CompanySettingService companySettingService,
                                PaymentPurchasePrepaymentService purchasePrepaymentService,
                                PurchaseOrderDownstreamMutationGuard downstreamMutationGuard) {
        super(snowflakeIdGenerator);
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.availabilityService = availabilityService;
        this.responseAssembler = responseAssembler;
        this.supplierResolver = supplierResolver;
        this.purchaseOrderApplyService = purchaseOrderApplyService;
        this.pieceWeightQueryService = pieceWeightQueryService;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.companySettingService = companySettingService;
        this.purchasePrepaymentService = purchasePrepaymentService;
        this.downstreamMutationGuard = downstreamMutationGuard;
    }

    @Autowired(required = false)
    void setSupplierLedgerLockService(SupplierLedgerLockService supplierLedgerLockService) {
        this.supplierLedgerLockService = supplierLedgerLockService;
    }

    private static final String[] PURCHASE_ORDER_SEARCH_FIELDS = {"orderNo", "supplierName"};


    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> page(PageQuery query, PageFilter filter) {
        Specification<PurchaseOrder> spec = Specs.<PurchaseOrder>keywordLike(filter.keyword(), PURCHASE_ORDER_SEARCH_FIELDS)
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.dateTimeBetweenDatesIfPresent("orderDate", filter.startDate(), filter.endDate()));
        return page(query, spec, purchaseOrderRepository);
    }

    @Transactional(readOnly = true)
    public java.util.List<PurchaseOrderResponse> search(String keyword, int maxSize) {
        return search(keyword, PURCHASE_ORDER_SEARCH_FIELDS, maxSize,
                null, purchaseOrderRepository);
    }

    @Transactional
    public PurchaseOrderResponse reopenPurchaseOrder(Long id) {
        PurchaseOrder order = purchaseOrderRepository.findByIdAndDeletedFlagFalseForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        DataScopeContext.assertCanAccess(order);
        if (StatusConstants.AUDITED.equals(order.getStatus())) {
            return toDetailResponse(order);
        }
        if (!StatusConstants.PURCHASE_COMPLETED.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "只有完成采购状态可以撤销完成");
        }
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "purchase-order",
                order.getStatus(),
                StatusConstants.AUDITED,
                StatusConstants.PURCHASE_COMPLETED
        );
        if (downstreamMutationGuard != null) {
            downstreamMutationGuard.assertCompletionReopenAllowed(order);
        }
        lockSupplierLedgerMutation(order);
        order.setStatus(StatusConstants.AUDITED);
        purchaseOrderRepository.save(order);
        return toDetailResponse(order);
    }

    private void lockSupplierLedgerMutation(PurchaseOrder order) {
        if (order.getSettlementCompanyId() == null || order.getSupplierId() == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单缺少供应商或结算主体身份，不能撤销完成采购"
            );
        }
        if (supplierLedgerLockService == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商账簿锁服务不可用");
        }
        supplierLedgerLockService.lock(order.getSettlementCompanyId(), order.getSupplierId());
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderImportCandidateResponse> importCandidates(PageQuery query, PageFilter filter, String usage) {
        PurchaseOrderAvailabilityService.ImportCandidateUsage candidateUsage =
                PurchaseOrderAvailabilityService.ImportCandidateUsage.from(usage);
        Specification<PurchaseOrder> spec = Specs.<PurchaseOrder>notDeleted()
                .and(Specs.keywordLike(filter.keyword(), PURCHASE_ORDER_SEARCH_FIELDS))
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.dateTimeBetweenDatesIfPresent("orderDate", filter.startDate(), filter.endDate()));
        return importableCandidates(query, spec, candidateUsage, filter.currentRecordId());
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderImportCandidateResponse> prepaymentCandidates(PageQuery query, PageFilter filter) {
        Specification<PurchaseOrder> spec = Specs.<PurchaseOrder>notDeleted()
                .and(Specs.keywordLike(filter.keyword(), PURCHASE_ORDER_SEARCH_FIELDS))
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(prepaymentSourceStatus(filter.status()))
                .and(Specs.dateTimeBetweenDatesIfPresent("orderDate", filter.startDate(), filter.endDate()));
        return pageEntities(query, spec, purchaseOrderRepository)
                .map(order -> toImportCandidateResponse(order, null));
    }

    private Page<PurchaseOrderImportCandidateResponse> importableCandidates(
            PageQuery query,
            Specification<PurchaseOrder> baseSpec,
            PurchaseOrderAvailabilityService.ImportCandidateUsage usage,
            Long currentRecordId
    ) {
        Set<String> allowedStatuses = usage == PurchaseOrderAvailabilityService.ImportCandidateUsage.SALES_ORDER
                ? StatusConstants.SALES_ORDER_SOURCE_PURCHASE_ORDER_STATUS
                : Set.of(StatusConstants.AUDITED);
        Specification<PurchaseOrder> spec = baseSpec.and(
                (root, criteriaQuery, criteriaBuilder) -> root.get("status").in(allowedStatuses)
        );
        List<PurchaseOrder> orders = new java.util.ArrayList<>();
        int pageIndex = 0;
        Page<PurchaseOrder> orderPage;
        do {
            orderPage = pageEntities(
                    PageQuery.of(pageIndex, 200, query.sortBy(), query.direction()),
                    spec,
                    purchaseOrderRepository
            );
            orders.addAll(orderPage.getContent());
            pageIndex++;
        } while (orderPage.hasNext());
        Map<Long, Integer> importableQuantityMap =
                availabilityService.buildImportableQuantityMap(
                        orders,
                        usage,
                        currentRecordId
                );
        List<PurchaseOrderImportCandidateResponse> candidates = orders.stream()
                .filter(order -> allowedStatuses.contains(order.getStatus()))
                .map(order -> toImportCandidateResponse(
                        order,
                        importableQuantityMap.getOrDefault(order.getId(), 0)
                ))
                .filter(candidate -> candidate.importableQuantity() > 0)
                .toList();
        int start = Math.min(query.page() * query.size(), candidates.size());
        int end = Math.min(start + query.size(), candidates.size());
        return new PageImpl<>(
                candidates.subList(start, end),
                query.toPageable("id"),
                candidates.size()
        );
    }

    @Override
    protected PurchaseOrderResponse toDetailResponse(PurchaseOrder order) {
        return responseAssembler.toDetailResponse(order);
    }

    private PurchaseOrderImportCandidateResponse toImportCandidateResponse(PurchaseOrder order, Integer importableQuantity) {
        return new PurchaseOrderImportCandidateResponse(
                order.getId(),
                order.getOrderNo(),
                order.getSupplierId(),
                order.getSupplierCode(),
                order.getSupplierName(),
                order.getSettlementCompanyId(),
                order.getSettlementCompanyName(),
                order.getBuyerName(),
                order.getOrderDate(),
                order.getTotalWeight(),
                order.getTotalAmount(),
                order.getStatus(),
                importableQuantity
        );
    }

    private Specification<PurchaseOrder> prepaymentSourceStatus(String status) {
        return (root, query, criteriaBuilder) -> {
            String requestedStatus = BusinessDocumentValidator.trimToNull(status);
            if (requestedStatus == null) {
                return root.get("status").in(PREPAYMENT_SOURCE_STATUSES);
            }
            if (!PREPAYMENT_SOURCE_STATUSES.contains(requestedStatus)) {
                return criteriaBuilder.disjunction();
            }
            return criteriaBuilder.equal(root.get("status"), requestedStatus);
        };
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
                resolveCreateBusinessNo("purchase-order", request.orderNo(), entityId),
                request.supplierId(),
                request.supplierCode(),
                request.supplierName(),
                request.orderDate(),
                request.buyerName(),
                request.settlementCompanyId(),
                request.status(),
                request.remark(),
                request.items()
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
                request.items()
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
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return StatusConstants.PURCHASE_ORDER_TRANSITIONS;
    }

    @Override
    protected void apply(PurchaseOrder purchaseOrder, PurchaseOrderRequest request) {
        assertLineQuantities(request);
        if (purchaseOrder.getId() != null) {
            if (downstreamMutationGuard != null
                    && purchaseOrder.getItems().stream().anyMatch(item -> item.getId() != null)) {
                downstreamMutationGuard.assertSourceLineMutationAllowed(purchaseOrder, request.items(), "修改");
            }
            assertNoActivePurchasePrepayment(purchaseOrder, "修改");
        }
        assertSettlementCompanyMutable(purchaseOrder, request.settlementCompanyId());
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                purchaseOrder.getStatus() != null ? purchaseOrder.getStatus() : StatusConstants.DRAFT,
                "采购订单状态",
                StatusConstants.ALLOWED_PURCHASE_ORDER_STATUS
        );
        assertStatusNotChangedBySave(purchaseOrder, nextStatus);
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "purchase-order",
                purchaseOrder.getStatus(),
                nextStatus,
                StatusConstants.AUDITED,
                StatusConstants.PURCHASE_COMPLETED
        );
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
        SettlementCompanySnapshot settlementCompany = resolveSettlementCompany(request.settlementCompanyId());
        purchaseOrder.setSettlementCompanyId(settlementCompany.id());
        purchaseOrder.setSettlementCompanyName(settlementCompany.name());
        purchaseOrder.setStatus(nextStatus);
        purchaseOrder.setRemark(request.remark());
        purchaseOrderApplyService.applyItems(purchaseOrder, request, this::nextId);
    }

    private void assertStatusNotChangedBySave(PurchaseOrder purchaseOrder, String requestedStatus) {
        String currentStatus = purchaseOrder.getStatus();
        if (currentStatus == null) {
            if (!StatusConstants.DRAFT.equals(requestedStatus)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "新建采购订单只能保存为草稿，审核请使用审核命令"
                );
            }
            return;
        }
        if (!currentStatus.equals(requestedStatus)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "普通保存不能修改采购订单状态，请使用审核、反审核或完成采购命令"
            );
        }
    }

    @Override
    protected void beforeStatusUpdate(PurchaseOrder entity, String currentStatus, String nextStatus) {
        if (StatusConstants.DRAFT.equals(currentStatus) && StatusConstants.AUDITED.equals(nextStatus)) {
            assertAuditableLineQuantities(entity);
        }
        if (StatusConstants.PURCHASE_COMPLETED.equals(nextStatus)
                && !StatusConstants.PURCHASE_COMPLETED.equals(currentStatus)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "完成采购必须使用完成采购接口"
            );
        }
        if (StatusConstants.PURCHASE_COMPLETED.equals(currentStatus)
                && StatusConstants.AUDITED.equals(nextStatus)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "完成采购不能通过通用状态接口撤销，请使用撤销完成采购接口"
            );
        }
        if (StatusConstants.DRAFT.equals(nextStatus)
                && !StatusConstants.DRAFT.equals(currentStatus)) {
            if (downstreamMutationGuard != null) {
                downstreamMutationGuard.assertMutable(entity, "反审核");
            }
            assertNoActivePurchasePrepayment(entity, "反审核");
        }
    }

    @Override
    protected void beforeDelete(PurchaseOrder entity) {
        if (downstreamMutationGuard != null) {
            downstreamMutationGuard.assertMutable(entity, "删除");
        }
        assertNoActivePurchasePrepayment(entity, "删除");
    }

    @Override
    protected PurchaseOrder saveEntity(PurchaseOrder entity) {
        return purchaseOrderRepository.save(entity);
    }

    @Override
    protected PurchaseOrderResponse toResponse(PurchaseOrder entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

    @Override
    protected PurchaseOrderResponse toSavedResponse(PurchaseOrder entity) {
        return toDetailResponse(entity);
    }

    public List<PieceWeightResponse> getPieceWeights(Long itemId) {
        return pieceWeightQueryService.getPieceWeights(itemId);
    }

    public List<PieceWeightResponse> getPieceWeightsBySalesOrderItemId(Long salesOrderItemId) {
        return pieceWeightQueryService.getPieceWeightsBySalesOrderItemId(salesOrderItemId);
    }

    private void assertSettlementCompanyMutable(PurchaseOrder purchaseOrder, Long requestedSettlementCompanyId) {
        if (purchaseOrder.getId() == null || purchaseOrder.getSettlementCompanyId() == null) {
            return;
        }
        if (StatusConstants.AUDITED.equals(purchaseOrder.getStatus())
                && !purchaseOrder.getSettlementCompanyId().equals(requestedSettlementCompanyId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核采购订单不允许修改采购结算主体");
        }
    }

    private void assertNoActivePurchasePrepayment(PurchaseOrder purchaseOrder, String action) {
        if (purchasePrepaymentService != null) {
            purchasePrepaymentService.assertNoActivePrepayment(purchaseOrder.getId(), action);
        }
    }

    private void assertLineQuantities(PurchaseOrderRequest request) {
        for (int index = 0; index < request.items().size(); index++) {
            Integer quantity = request.items().get(index).quantity();
            if (quantity == null || quantity < 1) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "第" + (index + 1) + "行数量必须至少为1个数量单位"
                );
            }
        }
    }

    private void assertAuditableLineQuantities(PurchaseOrder purchaseOrder) {
        for (var item : purchaseOrder.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + item.getLineNo() + "行数量必须至少为1个数量单位"
                );
            }
        }
    }

    private SettlementCompanySnapshot resolveSettlementCompany(Long id) {
        if (companySettingService == null) {
            return new SettlementCompanySnapshot(id, null);
        }
        CompanySetting company = companySettingService.requireActiveSettlementCompany(id);
        return new SettlementCompanySnapshot(company.getId(), company.getCompanyName());
    }

    private record SettlementCompanySnapshot(Long id, String name) {
    }
}
