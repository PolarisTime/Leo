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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final PurchaseInboundRefundGuard purchaseInboundRefundGuard;
    private final PurchaseInboundStatementGuard purchaseInboundStatementGuard;

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
                                  PurchaseInboundRefundGuard purchaseInboundRefundGuard,
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
        this.purchaseInboundRefundGuard = purchaseInboundRefundGuard;
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
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "采购入库状态",
                StatusConstants.ALLOWED_PURCHASE_INBOUND_STATUS
        );
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

    @Override
    protected void beforeDelete(PurchaseInbound inbound) {
        lockSourcePurchaseOrderItems(inbound, null);
        deleteService.beforeDelete(inbound);
    }

    @Override
    protected void beforeStatusUpdate(PurchaseInbound inbound, String currentStatus, String nextStatus) {
        lockSourcePurchaseOrderItems(inbound, null);
        purchaseInboundRefundGuard.assertStatusTransitionAllowed(inbound, currentStatus, nextStatus);
        purchaseInboundStatementGuard.assertStatusTransitionAllowed(inbound, currentStatus, nextStatus);
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
        return saveWithCompletionSync(entity, false);
    }

    @Override
    protected PurchaseInbound saveUpdatedEntity(PurchaseInbound entity, PurchaseInboundRequest request) {
        return saveWithCompletionSync(entity, false);
    }

    @Override
    protected PurchaseInbound saveStatusEntity(PurchaseInbound entity) {
        return saveWithCompletionSync(entity, true);
    }

    private PurchaseInbound saveWithCompletionSync(PurchaseInbound entity, boolean recalculateDraftSourceOrder) {
        boolean completedByServer = completionSyncService.shouldCompleteInbound(entity);
        if (completedByServer) {
            entity.setStatus(StatusConstants.INBOUND_COMPLETED);
        }
        boolean shouldRecalculateSourceOrder = completedByServer
                || (recalculateDraftSourceOrder && StatusConstants.DRAFT.equals(entity.getStatus()));
        PurchaseInbound saved = saveEntity(entity);
        if (shouldRecalculateSourceOrder) {
            completionSyncService.synchronizeSourcePurchaseOrders(saved);
        }
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
