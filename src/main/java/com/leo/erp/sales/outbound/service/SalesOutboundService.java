package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractStatusCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.web.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class SalesOutboundService extends AbstractStatusCrudService<
        SalesOutbound, SalesOutboundRequest, SalesOutboundResponse> {

    private static final String[] PRODUCT_SEARCH_FIELDS = {"materialCode", "brand", "material", "spec"};
    private static final String[] OUTBOUND_SEARCH_FIELDS = {"outboundNo", "salesOrderNo", "customerName", "projectName"};

    private final SalesOutboundRepository repository;
    private final SalesOutboundResponseAssembler responseAssembler;
    private final SalesOutboundWorkflowService workflowService;
    private final SalesOutboundDeleteRollbackService deleteRollbackService;
    private final SalesOutboundImportedUpdatePolicy importedUpdatePolicy;

    @Autowired
    public SalesOutboundService(SalesOutboundRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                SalesOutboundResponseAssembler responseAssembler,
                                SalesOutboundWorkflowService workflowService,
                                SalesOutboundDeleteRollbackService deleteRollbackService) {
        super(idGenerator);
        this.repository = repository;
        this.responseAssembler = responseAssembler;
        this.workflowService = workflowService;
        this.deleteRollbackService = deleteRollbackService;
        this.importedUpdatePolicy = new SalesOutboundImportedUpdatePolicy();
    }

    @Transactional(readOnly = true)
    public Page<SalesOutboundResponse> page(PageQuery query, PageFilter filter, String productKeyword) {
        Specification<SalesOutbound> spec = Specs.<SalesOutbound>keywordLike(filter.keyword(), OUTBOUND_SEARCH_FIELDS)
                .and(Specs.collectionKeywordLike(productKeyword, "items", PRODUCT_SEARCH_FIELDS))
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(Specs.equalValueIfPresent("customerId", filter.customerId()))
                .and(Specs.equalValueIfPresent("projectId", filter.projectId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.betweenIfPresent("outboundDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    @Transactional(readOnly = true)
    public java.util.List<SalesOutboundResponse> search(String keyword, int maxSize) {
        return search(keyword, OUTBOUND_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    @Transactional
    public SalesOutboundResponse create(SalesOutboundRequest request) {
        SalesOutboundResponse created = super.create(
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Override
    @Transactional
    public SalesOutboundResponse update(Long id, SalesOutboundRequest request) {
        SalesOutboundResponse updated = super.update(id,
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
    }

    @Override
    protected SalesOutboundResponse toDetailResponse(SalesOutbound entity) {
        return responseAssembler.toDetailResponse(entity);
    }

    @Override
    protected void validateCreate(SalesOutboundRequest request) {
        if (repository.existsByOutboundNoAndDeletedFlagFalse(request.outboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库单号已存在");
        }
        String status = request.status() == null ? "" : request.status().trim();
        if (!status.isEmpty() && !StatusConstants.DRAFT.equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新销售出库只能保存为草稿");
        }
        if (request.salesOrderNo() == null || request.salesOrderNo().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库必须从已审核销售订单导入");
        }
    }

    @Override
    protected void validateUpdate(SalesOutbound entity, SalesOutboundRequest request) {
        if (!entity.getOutboundNo().equals(request.outboundNo()) && repository.existsByOutboundNoAndDeletedFlagFalse(request.outboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库单号已存在");
        }
    }

    @Override
    protected SalesOutboundRequest normalizeCreateRequest(SalesOutboundRequest request, long entityId) {
        return new SalesOutboundRequest(
                resolveCreateBusinessNo(entityId),
                request.salesOrderNo(),
                request.customerId(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.warehouseId(),
                request.warehouseName(),
                request.outboundDate(),
                request.status(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    private SalesOutboundRequest withStatus(SalesOutboundRequest request, String status) {
        return new SalesOutboundRequest(
                request.outboundNo(),
                request.salesOrderNo(),
                request.customerId(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.warehouseId(),
                request.warehouseName(),
                request.outboundDate(),
                status,
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    @Override
    protected SalesOutboundRequest normalizeUpdateRequest(SalesOutbound entity, SalesOutboundRequest request) {
        return importedUpdatePolicy.normalizeUpdateRequest(entity, request);
    }

    @Override
    protected SalesOutbound newEntity() {
        return new SalesOutbound();
    }

    @Override
    protected void assignId(SalesOutbound entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<SalesOutbound> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<SalesOutbound> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "销售出库不存在";
    }

    @Override
    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.SALES_OUTBOUND_TRANSITIONS;
    }

    @Override
    protected void apply(SalesOutbound entity, SalesOutboundRequest request) {
        workflowService.apply(entity, request, this::nextId);
    }

    @Override
    protected void beforeStatusUpdate(SalesOutbound entity, String currentStatus, String nextStatus) {
        workflowService.beforeStatusUpdate(entity, currentStatus, nextStatus);
    }

    @Override
    @Transactional
    public SalesOutboundResponse updateStatus(Long id, String status) {
        SalesOutbound outbound = requireEntity(id);
        String currentStatus = outbound.getStatus();
        SalesOutboundResponse response = super.updateStatus(id, status);
        if (!Objects.equals(currentStatus, response.status())) {
            workflowService.publishStatusChanged(outbound, currentStatus, response.status());
        }
        return response;
    }

    @Override
    protected void beforeDelete(SalesOutbound entity) {
        workflowService.lockSourceSalesOrderItems(entity.getItems(), java.util.List.of());
        deleteRollbackService.beforeDelete(entity);
    }

    @Override
    protected void afterDelete(SalesOutbound entity) {
        workflowService.publishDeleted(entity);
    }

    @Override
    protected SalesOutbound saveEntity(SalesOutbound entity) {
        return workflowService.save(entity);
    }

    @Override
    protected SalesOutbound saveCreatedEntity(SalesOutbound entity, SalesOutboundRequest request) {
        return workflowService.saveCreated(entity, request);
    }

    @Override
    protected SalesOutbound saveUpdatedEntity(SalesOutbound entity, SalesOutboundRequest request) {
        return workflowService.saveUpdated(entity, request);
    }

    @Override
    protected SalesOutboundResponse toResponse(SalesOutbound entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

    @Override
    protected SalesOutboundResponse toSavedResponse(SalesOutbound entity) {
        return toDetailResponse(entity);
    }
}
