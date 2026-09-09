package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractStatusCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.purchase.inbound.web.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class PurchaseInboundService extends AbstractStatusCrudService<
        PurchaseInbound, PurchaseInboundRequest, PurchaseInboundResponse> {

    private static final String[] INBOUND_SEARCH_FIELDS = {"inboundNo", "purchaseOrderNo", "supplierName"};

    private final PurchaseInboundRepository repository;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseInboundApplyService applyService;
    private final PurchaseInboundResponseAssembler responseAssembler;
    private final PurchaseInboundMutationGuardService mutationGuardService;
    private final PurchaseInboundWorkflowService workflowService;

    @Autowired
    public PurchaseInboundService(PurchaseInboundRepository repository,
                                  SnowflakeIdGenerator idGenerator,
                                  PurchaseInboundMapper purchaseInboundMapper,
                                  PurchaseInboundApplyService applyService,
                                  PurchaseInboundResponseAssembler responseAssembler,
                                  PurchaseInboundMutationGuardService mutationGuardService,
                                  PurchaseInboundWorkflowService workflowService) {
        super(idGenerator);
        this.repository = repository;
        this.purchaseInboundMapper = purchaseInboundMapper;
        this.applyService = applyService;
        this.responseAssembler = responseAssembler;
        this.mutationGuardService = mutationGuardService;
        this.workflowService = workflowService;
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
    @Transactional
    public PurchaseInboundResponse create(PurchaseInboundRequest request) {
        PurchaseInboundResponse created = super.create(
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Override
    @Transactional
    public PurchaseInboundResponse update(Long id, PurchaseInboundRequest request) {
        PurchaseInboundResponse updated = super.update(id,
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
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
                request.items(),
                request.audit()
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
                request.items(),
                request.audit()
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
                request.items(),
                request.audit()
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
    protected java.util.Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.PURCHASE_INBOUND_TRANSITIONS;
    }

    @Override
    @Transactional
    public PurchaseInboundResponse updateStatus(Long id, String status) {
        PurchaseInbound inbound = requireEntity(id);
        String currentStatus = inbound.getStatus();
        PurchaseInboundResponse response = super.updateStatus(id, status);
        if (!Objects.equals(currentStatus, response.status())) {
            workflowService.publishStatusChanged(inbound, currentStatus, response.status());
        }
        return response;
    }

    @Override
    protected void apply(PurchaseInbound inbound, PurchaseInboundRequest request) {
        mutationGuardService.lockSourcePurchaseOrderItems(inbound, request);
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "采购入库状态",
                StatusConstants.ALLOWED_PURCHASE_INBOUND_STATUS
        );
        mutationGuardService.assertSaveDoesNotChangeStatus(inbound, nextStatus);
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
        mutationGuardService.assertDeletionAllowed(inbound);
    }

    @Override
    protected void afterDelete(PurchaseInbound inbound) {
        workflowService.afterDelete(inbound);
    }

    @Override
    protected void beforeStatusUpdate(PurchaseInbound inbound, String currentStatus, String nextStatus) {
        mutationGuardService.prepareStatusTransition(inbound, currentStatus, nextStatus);
        inbound.setSourcePurchaseOrderReopenAllowed(
                StatusConstants.DRAFT.equals(nextStatus)
                        && (StatusConstants.AUDITED.equals(currentStatus)
                        || StatusConstants.INBOUND_COMPLETED.equals(currentStatus))
        );
    }

    @Override
    protected PurchaseInbound saveEntity(PurchaseInbound entity) {
        return repository.save(entity);
    }

    @Override
    protected PurchaseInbound saveCreatedEntity(PurchaseInbound entity, PurchaseInboundRequest request) {
        return workflowService.saveCreated(entity, request);
    }

    @Override
    protected PurchaseInbound saveUpdatedEntity(PurchaseInbound entity, PurchaseInboundRequest request) {
        return workflowService.saveUpdated(entity, request);
    }

    @Override
    protected PurchaseInbound saveStatusEntity(PurchaseInbound entity) {
        return workflowService.saveStatus(entity);
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
