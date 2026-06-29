package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.outbound.web.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SalesOutboundService extends AbstractCrudService<SalesOutbound, SalesOutboundRequest, SalesOutboundResponse> {

    private final SalesOutboundRepository repository;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final SalesOutboundApplyService salesOutboundApplyService;
    private final SalesOutboundResponseAssembler responseAssembler;
    private final SalesOutboundSaveService saveService;

    @Autowired
    public SalesOutboundService(SalesOutboundRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                WorkflowTransitionGuard workflowTransitionGuard,
                                SalesOutboundApplyService salesOutboundApplyService,
                                SalesOutboundResponseAssembler responseAssembler,
                                SalesOutboundSaveService saveService) {
        super(idGenerator);
        this.repository = repository;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.salesOutboundApplyService = salesOutboundApplyService;
        this.responseAssembler = responseAssembler;
        this.saveService = saveService;
    }

    @Transactional(readOnly = true)
    public Page<SalesOutboundResponse> page(PageQuery query, PageFilter filter) {
        Specification<SalesOutbound> spec = Specs.<SalesOutbound>keywordLike(filter.keyword(), "outboundNo", "salesOrderNo", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("outboundDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    private static final String[] OUTBOUND_SEARCH_FIELDS = {"outboundNo", "salesOrderNo", "customerName", "projectName"};

    @Transactional(readOnly = true)
    public java.util.List<SalesOutboundResponse> search(String keyword, int maxSize) {
        return search(keyword, OUTBOUND_SEARCH_FIELDS, maxSize, null, repository);
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
                resolveCreateBusinessNo("sales-outbound", request.outboundNo(), entityId),
                null,
                request.customerName(),
                request.projectName(),
                request.warehouseName(),
                request.outboundDate(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected SalesOutboundRequest normalizeUpdateRequest(SalesOutbound entity, SalesOutboundRequest request) {
        return new SalesOutboundRequest(
                entity.getOutboundNo(),
                entity.getSalesOrderNo(),
                request.customerName(),
                request.projectName(),
                request.warehouseName(),
                request.outboundDate(),
                request.status(),
                request.remark(),
                request.items()
        );
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
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return StatusConstants.DRAFT_AUDIT_TRANSITIONS;
    }

    @Override
    protected void apply(SalesOutbound entity, SalesOutboundRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "销售出库状态",
                StatusConstants.ALLOWED_SALES_OUTBOUND_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "sales-outbound",
                entity.getStatus(),
                nextStatus,
                StatusConstants.AUDITED
        );
        entity.setOutboundNo(request.outboundNo());
        entity.setSalesOrderNo(request.salesOrderNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setOutboundDate(request.outboundDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());
        salesOutboundApplyService.applyItems(entity, request, this::nextId);
    }

    @Override
    protected SalesOutbound saveEntity(SalesOutbound entity) {
        return saveService.save(entity);
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
