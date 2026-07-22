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
import com.leo.erp.finance.payment.service.PaymentPurchasePrepaymentService;
import com.leo.erp.purchase.order.audit.PurchaseOrderAuditPublisher;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderInboundCandidateQueryRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderImportCandidateResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
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
    private final CompanySettingService companySettingService;
    private final PaymentPurchasePrepaymentService purchasePrepaymentService;
    private final PurchaseOrderDownstreamMutationGuard downstreamMutationGuard;
    private final PurchaseOrderAuditPublisher purchaseOrderAuditPublisher;
    private final PurchaseOrderInboundCandidateQueryRepository inboundCandidateQueryRepository;

    @Autowired
    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                SnowflakeIdGenerator snowflakeIdGenerator,
                                PurchaseOrderAvailabilityService availabilityService,
                                PurchaseOrderResponseAssembler responseAssembler,
                                PurchaseOrderSupplierResolver supplierResolver,
                                PurchaseOrderApplyService purchaseOrderApplyService,
                                CompanySettingService companySettingService,
                                PaymentPurchasePrepaymentService purchasePrepaymentService,
                                PurchaseOrderDownstreamMutationGuard downstreamMutationGuard,
                                PurchaseOrderAuditPublisher purchaseOrderAuditPublisher,
                                PurchaseOrderInboundCandidateQueryRepository inboundCandidateQueryRepository) {
        super(snowflakeIdGenerator);
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.availabilityService = availabilityService;
        this.responseAssembler = responseAssembler;
        this.supplierResolver = supplierResolver;
        this.purchaseOrderApplyService = purchaseOrderApplyService;
        this.companySettingService = companySettingService;
        this.purchasePrepaymentService = purchasePrepaymentService;
        this.downstreamMutationGuard = downstreamMutationGuard;
        this.purchaseOrderAuditPublisher = purchaseOrderAuditPublisher;
        this.inboundCandidateQueryRepository = inboundCandidateQueryRepository;
    }

    private static final String[] PURCHASE_ORDER_SEARCH_FIELDS = {"orderNo", "supplierName"};


    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> page(PageQuery query, PageFilter filter) {
        Specification<PurchaseOrder> spec = Specs.<PurchaseOrder>keywordLike(filter.keyword(), PURCHASE_ORDER_SEARCH_FIELDS)
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.dateTimeBetweenDatesIfPresent("orderDate", filter.startDate(), filter.endDate()));
        return page(query, spec, purchaseOrderRepository);
    }

    @Transactional(readOnly = true)
    public java.util.List<PurchaseOrderResponse> search(String keyword, int maxSize) {
        return search(keyword, PURCHASE_ORDER_SEARCH_FIELDS, maxSize,
                null, purchaseOrderRepository);
    }

    @Transactional
    public PurchaseOrderResponse createAndAudit(PurchaseOrderRequest request) {
        PurchaseOrderResponse created = create(withStatus(request, StatusConstants.DRAFT));
        return updateStatus(created.id(), StatusConstants.AUDITED);
    }

    @Transactional
    public PurchaseOrderResponse updateAndAudit(Long id, PurchaseOrderRequest request) {
        update(id, withStatus(request, StatusConstants.DRAFT));
        return updateStatus(id, StatusConstants.AUDITED);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<PurchaseOrderImportCandidateResponse> inboundImportCandidates(PageQuery query, PageFilter filter) {
        Page<Long> candidateIds = inboundCandidateQueryRepository.pageIds(query, filter);
        List<PurchaseOrder> orders = candidateIds.isEmpty()
                ? List.of()
                : purchaseOrderRepository.findByIdInAndDeletedFlagFalse(candidateIds.getContent());
        Map<Long, PurchaseOrder> orderById = orders.stream()
                .collect(Collectors.toMap(PurchaseOrder::getId, Function.identity()));
        List<PurchaseOrder> orderedOrders = candidateIds.getContent().stream()
                .map(orderById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, Integer> importableQuantityMap =
                availabilityService.buildInboundImportableQuantityMap(
                        orderedOrders,
                        filter.currentRecordId()
                );
        List<PurchaseOrderImportCandidateResponse> candidates = orderedOrders.stream()
                .map(order -> toImportCandidateResponse(
                        order,
                        importableQuantityMap.getOrDefault(order.getId(), 0)
                ))
                .filter(candidate -> candidate.importableQuantity() > 0)
                .toList();
        return new PageImpl<>(
                candidates,
                candidateIds.getPageable(),
                candidateIds.getTotalElements()
        );
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
                resolveCreateBusinessNo(entityId),
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
    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
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
        if (purchaseOrderAuditPublisher == null) {
            return;
        }

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
                    "普通保存不能修改采购订单状态，请使用审核或反审核操作"
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
    protected void afterDelete(PurchaseOrder entity) {
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
        if (purchaseOrderAuditPublisher == null) {
            return;
        }
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
