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
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
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
    private final SourceAllocationLockService sourceAllocationLockService;
    private final PurchaseInboundSourceStatusGuard purchaseInboundSourceStatusGuard;
    private final PurchaseInboundWeightWriteBackService weightWriteBackService;
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    @Autowired
    public PurchaseInboundService(PurchaseInboundRepository repository,
                                  SnowflakeIdGenerator idGenerator,
                                  PurchaseInboundMapper purchaseInboundMapper,
                                  PurchaseInboundApplyService applyService,
                                  PurchaseInboundDeleteService deleteService,
                                  PurchaseInboundCompletionSyncService completionSyncService,
                                  PurchaseInboundResponseAssembler responseAssembler,
                                  SourceAllocationLockService sourceAllocationLockService,
                                  PurchaseInboundWeightWriteBackService weightWriteBackService,
                                  PurchaseInboundSourceStatusGuard purchaseInboundSourceStatusGuard) {
        super(idGenerator);
        this.repository = repository;
        this.purchaseInboundMapper = purchaseInboundMapper;
        this.applyService = applyService;
        this.deleteService = deleteService;
        this.completionSyncService = completionSyncService;
        this.responseAssembler = responseAssembler;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.weightWriteBackService = weightWriteBackService;
        this.purchaseInboundSourceStatusGuard = purchaseInboundSourceStatusGuard;
    }

    @Autowired(required = false)
    void setBusinessOperationEventPublisher(BusinessOperationEventPublisher publisher) {
        this.businessOperationEventPublisher = publisher;
    }

    @Transactional(readOnly = true)
    public Page<PurchaseInboundResponse> page(PageQuery query, PageFilter filter) {
        Specification<PurchaseInbound> spec = Specs.<PurchaseInbound>keywordLike(
                        filter.keyword(),
                        "inboundNo", "purchaseOrderNo", "supplierName"
                )
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.documentStatus(filter.status()))
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

    @Transactional
    public PurchaseInboundResponse createAndAudit(PurchaseInboundRequest request) {
        PurchaseInboundResponse created = create(withStatus(request, StatusConstants.DRAFT));
        return updateStatus(created.id(), StatusConstants.AUDITED);
    }

    @Transactional
    public PurchaseInboundResponse updateAndAudit(Long id, PurchaseInboundRequest request) {
        update(id, withStatus(request, StatusConstants.DRAFT));
        return updateStatus(id, StatusConstants.AUDITED);
    }

    @Override
    protected PurchaseInboundResponse toDetailResponse(PurchaseInbound inbound) {
        return responseAssembler.toDetailResponse(inbound);
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
                resolveCreateBusinessNo(entityId),
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

    private PurchaseInboundRequest withStatus(PurchaseInboundRequest request, String status) {
        return new PurchaseInboundRequest(
                request.inboundNo(),
                request.purchaseOrderNo(),
                request.supplierId(),
                request.supplierCode(),
                request.supplierName(),
                request.warehouseId(),
                request.warehouseName(),
                request.inboundDate(),
                request.settlementMode(),
                status,
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
    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return StatusConstants.PURCHASE_INBOUND_TRANSITIONS;
    }

    @Override
    @Transactional
    public PurchaseInboundResponse updateStatus(Long id, String status) {
        PurchaseInbound inbound = requireEntity(id);
        String currentStatus = inbound.getStatus();
        PurchaseInboundResponse response = super.updateStatus(id, status);
        if (!Objects.equals(currentStatus, response.status())) {
            String actionType = StatusConstants.DRAFT.equals(response.status()) ? "反审核" : "状态变更";
            publishEvent(inbound, "PURCHASE_INBOUND_STATUS_CHANGED", actionType,
                    "采购入库状态 " + currentStatus + " -> " + response.status());
        }
        return response;
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
        assertStatusNotChangedBySave(inbound, nextStatus);
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
        purchaseInboundSourceStatusGuard.assertDeletionAllowed(inbound);
    }

    @Override
    protected void afterDelete(PurchaseInbound inbound) {
        repository.flush();
        deleteService.afterDelete(inbound);
        publishEvent(inbound, "PURCHASE_INBOUND_DELETED", "删除", "删除采购入库 " + inbound.getInboundNo());
    }

    @Override
    protected void beforeStatusUpdate(PurchaseInbound inbound, String currentStatus, String nextStatus) {
        prepareStatusTransition(inbound, currentStatus, nextStatus);
        inbound.setSourcePurchaseOrderReopenAllowed(
                StatusConstants.DRAFT.equals(nextStatus)
                        && (StatusConstants.AUDITED.equals(currentStatus)
                        || StatusConstants.INBOUND_COMPLETED.equals(currentStatus))
        );
    }

    private void prepareStatusTransition(PurchaseInbound inbound, String currentStatus, String nextStatus) {
        lockSourcePurchaseOrderItems(inbound, null);
        if (!StatusConstants.DRAFT.equals(nextStatus)) {
            assertAuditableLineItems(inbound);
        }
        purchaseInboundSourceStatusGuard.assertStatusTransitionAllowed(inbound, currentStatus, nextStatus);
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
        PurchaseInbound saved = saveWithCompletionSync(entity);
        publishEvent(saved, "PURCHASE_INBOUND_CREATED", "新增", "新增采购入库 " + saved.getInboundNo());
        return saved;
    }

    @Override
    protected PurchaseInbound saveUpdatedEntity(PurchaseInbound entity, PurchaseInboundRequest request) {
        PurchaseInbound saved = saveWithCompletionSync(entity);
        publishEvent(saved, "PURCHASE_INBOUND_UPDATED", "编辑", "编辑采购入库 " + saved.getInboundNo());
        return saved;
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
        repository.flush();
        weightWriteBackService.synchronizeAfterSave(saved);
        completionSyncService.synchronizeSourcePurchaseOrders(
                saved,
                saved.isSourcePurchaseOrderReopenAllowed()
        );
        saved.setSourcePurchaseOrderReopenAllowed(false);
        return saved;
    }

    private void publishEvent(PurchaseInbound inbound, String eventType, String actionType, String remark) {
        if (businessOperationEventPublisher == null) {
            return;
        }
        businessOperationEventPublisher.publish(
                eventType,
                "purchase-inbound",
                "采购入库",
                actionType,
                "PurchaseInbound",
                inbound.getId(),
                inbound.getInboundNo(),
                remark
        );
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
