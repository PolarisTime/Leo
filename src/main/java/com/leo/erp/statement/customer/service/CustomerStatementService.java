package com.leo.erp.statement.customer.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CustomerStatementService extends AbstractCrudService<CustomerStatement, CustomerStatementRequest, CustomerStatementResponse> {

    private final CustomerStatementRepository repository;
    private final CustomerStatementResponseAssembler responseAssembler;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final CustomerStatementSourceService customerStatementSourceService;
    private final CustomerStatementApplyService applyService;

    @Autowired
    public CustomerStatementService(CustomerStatementRepository repository,
                                    SnowflakeIdGenerator idGenerator,
                                    CustomerStatementResponseAssembler responseAssembler,
                                    WorkflowTransitionGuard workflowTransitionGuard,
                                    CustomerStatementSourceService customerStatementSourceService,
                                    CustomerStatementApplyService applyService) {
        super(idGenerator);
        this.repository = repository;
        this.responseAssembler = responseAssembler;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.customerStatementSourceService = customerStatementSourceService;
        this.applyService = applyService;
    }

    @Transactional(readOnly = true)
    public Page<CustomerStatementResponse> page(PageQuery query, PageFilter filter) {
        Specification<CustomerStatement> spec = Specs.<CustomerStatement>keywordLike(filter.keyword(), "statementNo", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("endDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    private static final String[] CUSTOMER_STATEMENT_SEARCH_FIELDS = {
            "statementNo",
            "customerName",
            "projectName"
    };

    @Transactional(readOnly = true)
    public List<CustomerStatementResponse> search(String keyword, int maxSize) {
        return search(keyword, CUSTOMER_STATEMENT_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Transactional(readOnly = true)
    public Page<CustomerStatementCandidateResponse> candidatePage(PageQuery query, PageFilter filter) {
        return customerStatementSourceService.candidatePage(query, filter);
    }

    @Override
    protected CustomerStatementResponse toDetailResponse(CustomerStatement entity) {
        return responseAssembler.toDetailResponse(entity);
    }

    @Override
    protected CustomerStatementResponse toSavedResponse(CustomerStatement entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void validateCreate(CustomerStatementRequest request) {
        if (repository.existsByStatementNoAndDeletedFlagFalse(request.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户对账单号已存在");
        }
    }

    @Override
    protected void validateUpdate(CustomerStatement entity, CustomerStatementRequest request) {
        if (!entity.getStatementNo().equals(request.statementNo())
                && repository.existsByStatementNoAndDeletedFlagFalse(request.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户对账单号已存在");
        }
    }

    @Override
    protected CustomerStatementRequest normalizeCreateRequest(CustomerStatementRequest request, long entityId) {
        return new CustomerStatementRequest(
                resolveCreateBusinessNo("customer-statement", request.statementNo(), entityId),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.startDate(),
                request.endDate(),
                request.salesAmount(),
                request.receiptAmount(),
                request.closingAmount(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected CustomerStatementRequest normalizeUpdateRequest(CustomerStatement entity, CustomerStatementRequest request) {
        return new CustomerStatementRequest(
                entity.getStatementNo(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.startDate(),
                request.endDate(),
                request.salesAmount(),
                request.receiptAmount(),
                request.closingAmount(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected CustomerStatement newEntity() {
        return new CustomerStatement();
    }

    @Override
    protected void assignId(CustomerStatement entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<CustomerStatement> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<CustomerStatement> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "客户对账单不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected Set<String> allowedStatusTransitions() {
        return StatusConstants.STATEMENT_CONFIRM_TRANSITIONS;
    }

    @Override
    protected void beforeStatusUpdate(CustomerStatement entity, String currentStatus, String nextStatus) {
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "customer-statement",
                currentStatus,
                nextStatus,
                StatusConstants.CONFIRMED
        );
    }

    @Override
    protected void apply(CustomerStatement entity, CustomerStatementRequest request) {
        applyService.apply(entity, request, this::nextId);
    }

    @Override
    protected CustomerStatement saveEntity(CustomerStatement entity) {
        return repository.save(entity);
    }

    @Override
    protected CustomerStatementResponse toResponse(CustomerStatement entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

}
