package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.order.web.dto.PieceWeightResponse;
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.purchase.inbound.web.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class PurchaseInboundService extends AbstractCrudService<
        PurchaseInbound, PurchaseInboundRequest, PurchaseInboundResponse> {

    private final PurchaseInboundRepository repository;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseInboundApplyService applyService;
    private final PurchaseInboundDeleteService deleteService;
    private final PurchaseInboundCompletionSyncService completionSyncService;
    private final PurchaseInboundResponseAssembler responseAssembler;
    private final PurchaseInboundPieceWeightService pieceWeightService;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final PurchaseInboundSourceStatusGuard purchaseInboundSourceStatusGuard;
    private final PurchaseInboundStatementGuard purchaseInboundStatementGuard;
    private final PurchaseInboundWeightSettlementService weightSettlementService;

    private static final Set<String> TOLERANCE_REASON_CODES = Set.of(
            "TRANSPORT_LOSS",
            "HANDLING_LOSS",
            "MEASUREMENT_DIFFERENCE",
            "SUPPLIER_CONFIRMED",
            "MOISTURE_OR_IMPURITY_CHANGE",
            "THEORETICAL_WEIGHT_DEVIATION",
            "OTHER"
    );

    @Autowired
    public PurchaseInboundService(PurchaseInboundRepository repository,
                                  SnowflakeIdGenerator idGenerator,
                                  PurchaseInboundMapper purchaseInboundMapper,
                                  PurchaseInboundApplyService applyService,
                                  PurchaseInboundDeleteService deleteService,
                                  PurchaseInboundCompletionSyncService completionSyncService,
                                  PurchaseInboundResponseAssembler responseAssembler,
                                  PurchaseInboundPieceWeightService pieceWeightService,
                                  WorkflowTransitionGuard workflowTransitionGuard,
                                  SourceAllocationLockService sourceAllocationLockService,
                                  PurchaseInboundWeightSettlementService weightSettlementService,
                                  PurchaseInboundSourceStatusGuard purchaseInboundSourceStatusGuard,
                                  PurchaseInboundStatementGuard purchaseInboundStatementGuard) {
        super(idGenerator);
        this.repository = repository;
        this.purchaseInboundMapper = purchaseInboundMapper;
        this.applyService = applyService;
        this.deleteService = deleteService;
        this.completionSyncService = completionSyncService;
        this.responseAssembler = responseAssembler;
        this.pieceWeightService = pieceWeightService;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.weightSettlementService = weightSettlementService;
        this.purchaseInboundSourceStatusGuard = purchaseInboundSourceStatusGuard;
        this.purchaseInboundStatementGuard = purchaseInboundStatementGuard;
    }

    @Transactional(readOnly = true)
    public Page<PurchaseInboundResponse> page(PageQuery query, PageFilter filter) {
        Specification<PurchaseInbound> spec = Specs.<PurchaseInbound>keywordLike(
                        filter.keyword(),
                        "inboundNo", "purchaseOrderNo", "supplierName"
                )
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent(
                        "inboundDate", filter.startDate(), filter.endDate()
                ));
        Page<PurchaseInbound> page = pageEntities(query, spec, repository);
        Map<Long, PurchaseInboundItemRepository.InboundWeightSummary> weightSummaryMap =
                responseAssembler.loadInboundWeightSummaryMap(page.getContent());
        return page.map(inbound -> responseAssembler.toListResponse(inbound, weightSummaryMap.get(inbound.getId())));
    }

    private static final String[] INBOUND_SEARCH_FIELDS = {"inboundNo", "purchaseOrderNo", "supplierName"};

    @Transactional(readOnly = true)
    public java.util.List<PurchaseInboundResponse> search(String keyword, int maxSize) {
        java.util.List<PurchaseInboundResponse> responses = search(keyword, INBOUND_SEARCH_FIELDS, maxSize, null, repository);
        Map<Long, PurchaseInboundItemRepository.InboundWeightSummary> weightSummaryMap =
                responseAssembler.loadInboundWeightSummaryMapByIds(responses.stream()
                        .map(PurchaseInboundResponse::id)
                        .distinct()
                        .toList());
        return responses.stream()
                .map(response -> responseAssembler.withInboundWeightSummary(response, weightSummaryMap.get(response.id())))
                .toList();
    }

    @Override
    protected PurchaseInboundResponse toDetailResponse(PurchaseInbound inbound) {
        return responseAssembler.toDetailResponse(inbound);
    }

    @Transactional(readOnly = true)
    public List<PieceWeightResponse> getPieceWeights(Long itemId) {
        return pieceWeightService.getPieceWeights(itemId);
    }

    @Transactional
    public PurchaseInboundResponse audit(
            Long id,
            List<PurchaseInboundAuditRequest.OverToleranceConfirmation> confirmations
    ) {
        PurchaseInbound inbound = requireEntity(id);
        if (StatusConstants.AUDITED.equals(inbound.getStatus())
                || StatusConstants.INBOUND_COMPLETED.equals(inbound.getStatus())) {
            completionSyncService.synchronizeSourcePurchaseOrders(inbound);
            return toSavedResponse(inbound);
        }
        if (!StatusConstants.DRAFT.equals(inbound.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "只有草稿采购入库单可以审核");
        }
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "purchase-inbound",
                inbound.getStatus(),
                StatusConstants.AUDITED,
                StatusConstants.AUDITED,
                StatusConstants.INBOUND_COMPLETED
        );
        prepareStatusTransition(inbound, inbound.getStatus(), StatusConstants.AUDITED);
        applyToleranceConfirmations(inbound, confirmations == null ? List.of() : confirmations);
        inbound.setStatus(StatusConstants.AUDITED);
        PurchaseInbound saved = saveStatusEntity(inbound);
        return toSavedResponse(saved);
    }

    @Override
    protected void validateCreate(PurchaseInboundRequest request) {
        if (repository.existsByInboundNoAndDeletedFlagFalse(request.inboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购入库单号已存在");
        }
    }

    @Override
    protected void validateUpdate(PurchaseInbound inbound, PurchaseInboundRequest request) {
        boolean noChanged = !inbound.getInboundNo().equals(request.inboundNo());
        boolean noExists = repository.existsByInboundNoAndDeletedFlagFalse(
                request.inboundNo()
        );
        if (noChanged && noExists) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购入库单号已存在");
        }
    }

    @Override
    protected PurchaseInboundRequest normalizeCreateRequest(PurchaseInboundRequest request, long entityId) {
        return new PurchaseInboundRequest(
                resolveCreateBusinessNo("purchase-inbound", request.inboundNo(), entityId),
                request.purchaseOrderNo(),
                request.supplierId(),
                request.supplierCode(),
                request.supplierName(),
                request.warehouseId(),
                request.warehouseName(),
                request.inboundDate(),
                request.settlementMode(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected PurchaseInboundRequest normalizeUpdateRequest(PurchaseInbound entity, PurchaseInboundRequest request) {
        return new PurchaseInboundRequest(
                entity.getInboundNo(),
                request.purchaseOrderNo(),
                request.supplierId() == null ? entity.getSupplierId() : request.supplierId(),
                request.supplierCode() == null || request.supplierCode().isBlank()
                        ? entity.getSupplierCode()
                        : request.supplierCode(),
                request.supplierName(),
                request.warehouseId() == null ? entity.getWarehouseId() : request.warehouseId(),
                request.warehouseName(),
                request.inboundDate(),
                request.settlementMode(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected PurchaseInbound newEntity() {
        return new PurchaseInbound();
    }

    @Override
    protected void assignId(PurchaseInbound entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<PurchaseInbound> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<PurchaseInbound> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "采购入库不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return StatusConstants.PURCHASE_INBOUND_TRANSITIONS;
    }

    @Override
    protected void apply(PurchaseInbound inbound, PurchaseInboundRequest request) {
        lockSourcePurchaseOrderItems(inbound, request);
        if (inbound.getItems().stream().anyMatch(item -> item.getId() != null)) {
            purchaseInboundStatementGuard.assertSourceLineMutationAllowed(inbound, request.items(), "修改");
        }
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "采购入库状态",
                StatusConstants.ALLOWED_PURCHASE_INBOUND_STATUS
        );
        assertStatusNotChangedBySave(inbound, nextStatus);
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "purchase-inbound",
                inbound.getStatus(),
                nextStatus,
                StatusConstants.AUDITED,
                StatusConstants.INBOUND_COMPLETED
        );
        inbound.setInboundNo(request.inboundNo());
        inbound.setPurchaseOrderNo(request.purchaseOrderNo());
        inbound.setSupplierId(request.supplierId());
        inbound.setSupplierCode(request.supplierCode());
        inbound.setSupplierName(request.supplierName());
        inbound.setWarehouseId(request.warehouseId());
        inbound.setInboundDate(request.inboundDate());
        inbound.setStatus(nextStatus);
        inbound.setRemark(request.remark());
        applyService.applyItems(inbound, request, this::nextId);
    }

    private void assertStatusNotChangedBySave(PurchaseInbound inbound, String requestedStatus) {
        String currentStatus = inbound.getStatus();
        if (currentStatus == null) {
            if (!StatusConstants.DRAFT.equals(requestedStatus)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "新建采购入库只能保存为草稿，审核请使用审核命令"
                );
            }
            return;
        }
        if (!currentStatus.equals(requestedStatus)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "普通保存不能修改采购入库状态，请使用审核或反审核命令"
            );
        }
    }

    @Override
    protected void beforeDelete(PurchaseInbound inbound) {
        lockSourcePurchaseOrderItems(inbound, null);
        purchaseInboundStatementGuard.assertMutable(inbound, "删除");
        deleteService.beforeDelete(inbound);
    }

    @Override
    protected void beforeStatusUpdate(PurchaseInbound inbound, String currentStatus, String nextStatus) {
        if (StatusConstants.DRAFT.equals(currentStatus) && StatusConstants.AUDITED.equals(nextStatus)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购入库审核必须使用专用审核接口并提交超差确认"
            );
        }
        prepareStatusTransition(inbound, currentStatus, nextStatus);
        if (StatusConstants.DRAFT.equals(nextStatus)) {
            inbound.getItems().forEach(this::clearToleranceConfirmation);
        }
    }

    private void prepareStatusTransition(PurchaseInbound inbound, String currentStatus, String nextStatus) {
        lockSourcePurchaseOrderItems(inbound, null);
        if (!StatusConstants.DRAFT.equals(nextStatus)) {
            assertAuditableLineItems(inbound);
        }
        purchaseInboundSourceStatusGuard.assertStatusTransitionAllowed(inbound, currentStatus, nextStatus);
        purchaseInboundStatementGuard.assertStatusTransitionAllowed(inbound, currentStatus, nextStatus);
    }

    private void applyToleranceConfirmations(
            PurchaseInbound inbound,
            List<PurchaseInboundAuditRequest.OverToleranceConfirmation> confirmations
    ) {
        Map<Long, PurchaseInboundAuditRequest.OverToleranceConfirmation> confirmationMap = new HashMap<>();
        for (PurchaseInboundAuditRequest.OverToleranceConfirmation confirmation : confirmations) {
            if (confirmation == null || confirmation.inboundItemId() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "超差确认必须关联采购入库明细");
            }
            if (confirmationMap.putIfAbsent(confirmation.inboundItemId(), confirmation) != null) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "采购入库明细 " + confirmation.inboundItemId() + " 存在重复超差确认"
                );
            }
        }
        Map<String, PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule> ruleMap =
                weightSettlementService.loadPurchaseWeighCategoryRules(
                        inbound.getItems().stream().map(PurchaseInboundItem::getCategory).toList()
                );
        Set<Long> consumedConfirmationIds = new HashSet<>();
        for (PurchaseInboundItem item : inbound.getItems()) {
            String category = item.getCategory() == null ? "" : item.getCategory().trim();
            PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule rule = ruleMap.getOrDefault(
                    category,
                    new PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule(
                            category,
                            false,
                            new BigDecimal("5.00"),
                            new BigDecimal("5.00")
                    )
            );
            boolean weighSettlement = "过磅".equals(item.getSettlementMode());
            if (rule.purchaseWeighRequired() != weighSettlement) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + item.getLineNo() + "行品类过磅规则已变化，请退回草稿重新保存后再审核"
                );
            }
            if (!weighSettlement) {
                clearToleranceConfirmation(item);
                continue;
            }
            BigDecimal theoreticalWeight = TradeItemCalculator.calculateWeightTon(
                    item.getQuantity(),
                    item.getPieceWeightTon()
            );
            PurchaseInboundWeightSettlementService.ToleranceAssessment assessment =
                    weightSettlementService.assessTolerance(
                            item.getWeighWeightTon(),
                            theoreticalWeight,
                            rule
                    );
            if (!assessment.overTolerance()) {
                clearToleranceConfirmation(item);
                continue;
            }
            PurchaseInboundAuditRequest.OverToleranceConfirmation confirmation = confirmationMap.get(item.getId());
            if (confirmation == null) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + item.getLineNo() + "行过磅重量" + assessment.direction()
                                + "超出允许范围，必须选择超差原因后审核"
                );
            }
            applyToleranceConfirmation(item, assessment, confirmation);
            consumedConfirmationIds.add(item.getId());
        }
        confirmationMap.keySet().stream()
                .filter(id -> !consumedConfirmationIds.contains(id))
                .findFirst()
                .ifPresent(id -> {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "采购入库明细 " + id + " 当前未超差，不能提交超差确认"
                    );
                });
    }

    private void applyToleranceConfirmation(
            PurchaseInboundItem item,
            PurchaseInboundWeightSettlementService.ToleranceAssessment assessment,
            PurchaseInboundAuditRequest.OverToleranceConfirmation confirmation
    ) {
        String reasonCode = confirmation.reasonCode() == null
                ? ""
                : confirmation.reasonCode().trim().toUpperCase(java.util.Locale.ROOT);
        if (!TOLERANCE_REASON_CODES.contains(reasonCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + item.getLineNo() + "行超差原因不合法");
        }
        String remark = confirmation.remark() == null ? null : confirmation.remark().trim();
        if ("OTHER".equals(reasonCode) && (remark == null || remark.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + item.getLineNo() + "行选择其他原因时必须填写备注");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long operatorId = authentication != null
                && authentication.getPrincipal() instanceof com.leo.erp.security.support.SecurityPrincipal principal
                ? principal.id()
                : 0L;
        String operatorName = authentication == null || authentication.getName() == null
                ? "system"
                : authentication.getName().trim();
        if (operatorName.isBlank()) {
            operatorName = "system";
        }
        if (operatorName.length() > 64) {
            operatorName = operatorName.substring(0, 64);
        }
        item.setToleranceDirection(assessment.direction());
        item.setToleranceLimitPercent(assessment.limitPercent());
        item.setToleranceActualPercent(assessment.actualPercent());
        item.setToleranceReasonCode(reasonCode);
        item.setToleranceRemark(remark == null || remark.isBlank() ? null : remark);
        item.setToleranceConfirmedBy(operatorId);
        item.setToleranceConfirmedName(operatorName);
        item.setToleranceConfirmedAt(LocalDateTime.now());
    }

    private void clearToleranceConfirmation(PurchaseInboundItem item) {
        item.setToleranceDirection(null);
        item.setToleranceLimitPercent(null);
        item.setToleranceActualPercent(null);
        item.setToleranceReasonCode(null);
        item.setToleranceRemark(null);
        item.setToleranceConfirmedBy(null);
        item.setToleranceConfirmedName(null);
        item.setToleranceConfirmedAt(null);
    }

    private void assertAuditableLineItems(PurchaseInbound inbound) {
        for (PurchaseInboundItem item : inbound.getItems()) {
            int lineNo = item.getLineNo() == null ? 0 : item.getLineNo();
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + lineNo + "行入库数量必须大于0"
                );
            }
            if ("过磅".equals(item.getSettlementMode())
                    && (item.getWeighWeightTon() == null
                    || item.getWeighWeightTon().signum() <= 0)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + lineNo + "行需填写大于0的过磅重量后才能审核"
                );
            }
        }
    }

    private void lockSourcePurchaseOrderItems(PurchaseInbound inbound, PurchaseInboundRequest request) {
        Stream<Long> existingSourceIds = inbound == null
                ? Stream.empty()
                : inbound.getItems().stream().map(PurchaseInboundItem::getSourcePurchaseOrderItemId);
        Stream<Long> requestedSourceIds = request == null
                ? Stream.empty()
                : request.items().stream().map(PurchaseInboundItemRequest::sourcePurchaseOrderItemId);
        List<Long> sourceIds = Stream.concat(existingSourceIds, requestedSourceIds)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        sourceAllocationLockService.lockTradeItemSources(sourceIds, List.of(), List.of());
    }

    @Override
    protected PurchaseInbound saveEntity(PurchaseInbound entity) {
        return repository.save(entity);
    }

    @Override
    protected PurchaseInbound saveCreatedEntity(PurchaseInbound entity, PurchaseInboundRequest request) {
        return saveWithCompletionSync(entity);
    }

    @Override
    protected PurchaseInbound saveUpdatedEntity(PurchaseInbound entity, PurchaseInboundRequest request) {
        return saveWithCompletionSync(entity);
    }

    @Override
    protected PurchaseInbound saveStatusEntity(PurchaseInbound entity) {
        return saveWithCompletionSync(entity);
    }

    private PurchaseInbound saveWithCompletionSync(PurchaseInbound entity) {
        boolean completedByServer = completionSyncService.shouldCompleteInbound(entity);
        if (completedByServer) {
            entity.setStatus(StatusConstants.INBOUND_COMPLETED);
        }
        PurchaseInbound saved = saveEntity(entity);
        completionSyncService.synchronizeSourcePurchaseOrders(saved);
        return saved;
    }

    @Override
    protected PurchaseInboundResponse toResponse(PurchaseInbound entity) {
        return purchaseInboundMapper.toResponse(entity);
    }

    @Override
    protected PurchaseInboundResponse toSavedResponse(PurchaseInbound entity) {
        return toDetailResponse(entity);
    }
}
