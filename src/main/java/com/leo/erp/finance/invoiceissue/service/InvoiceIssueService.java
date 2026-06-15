package com.leo.erp.finance.invoiceissue.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class InvoiceIssueService extends AbstractCrudService<InvoiceIssue, InvoiceIssueRequest, InvoiceIssueResponse> {

    private final InvoiceIssueRepository repository;
    private final InvoiceIssueApplyService applyService;
    private final InvoiceIssueSourceService invoiceIssueSourceService;
    private final InvoiceIssueResponseAssembler responseAssembler;

    public InvoiceIssueService(InvoiceIssueRepository repository,
                               SnowflakeIdGenerator idGenerator,
                               InvoiceIssueApplyService applyService,
                               InvoiceIssueSourceService invoiceIssueSourceService,
                               InvoiceIssueResponseAssembler responseAssembler) {
        super(idGenerator);
        this.repository = repository;
        this.applyService = applyService;
        this.invoiceIssueSourceService = invoiceIssueSourceService;
        this.responseAssembler = responseAssembler;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceIssueResponse> page(PageQuery query, PageFilter filter) {
        Specification<InvoiceIssue> spec = Specs.<InvoiceIssue>keywordLike(filter.keyword(), "issueNo", "invoiceNo", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("invoiceDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    private static final String[] INVOICE_ISSUE_SEARCH_FIELDS = {
            "issueNo",
            "invoiceNo",
            "customerName",
            "projectName"
    };

    @Transactional(readOnly = true)
    public List<InvoiceIssueResponse> search(String keyword, int maxSize) {
        return search(keyword, INVOICE_ISSUE_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected InvoiceIssueResponse toDetailResponse(InvoiceIssue entity) {
        return responseAssembler.toDetailResponse(entity);
    }

    @Override
    protected InvoiceIssueResponse toSavedResponse(InvoiceIssue entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void validateCreate(InvoiceIssueRequest request) {
        if (repository.existsByIssueNoAndDeletedFlagFalse(request.issueNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "开票单号已存在");
        }
    }

    @Override
    protected void validateUpdate(InvoiceIssue entity, InvoiceIssueRequest request) {
        if (!entity.getIssueNo().equals(request.issueNo())
                && repository.existsByIssueNoAndDeletedFlagFalse(request.issueNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "开票单号已存在");
        }
    }

    @Override
    protected InvoiceIssueRequest normalizeCreateRequest(InvoiceIssueRequest request, long entityId) {
        return new InvoiceIssueRequest(
                resolveCreateBusinessNo("invoice-issue", request.issueNo(), entityId),
                request.invoiceNo(),
                request.customerName(),
                request.projectName(),
                request.invoiceDate(),
                request.invoiceType(),
                request.amount(),
                request.taxAmount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected InvoiceIssueRequest normalizeUpdateRequest(InvoiceIssue entity, InvoiceIssueRequest request) {
        return new InvoiceIssueRequest(
                entity.getIssueNo(),
                request.invoiceNo(),
                request.customerName(),
                request.projectName(),
                request.invoiceDate(),
                request.invoiceType(),
                request.amount(),
                request.taxAmount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected InvoiceIssue newEntity() {
        return new InvoiceIssue();
    }

    @Override
    protected void assignId(InvoiceIssue entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<InvoiceIssue> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<InvoiceIssue> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "开票单不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return java.util.Set.of(
                StatusConstants.DRAFT + "->" + StatusConstants.ISSUED,
                StatusConstants.ISSUED + "->" + StatusConstants.DRAFT
        );
    }

    @Override
    protected void beforeStatusUpdate(InvoiceIssue entity, String currentStatus, String nextStatus) {
        if (!StatusConstants.ISSUED.equals(nextStatus)) {
            return;
        }
        invoiceIssueSourceService.validateExistingItemsForIssue(entity);
    }

    @Override
    protected void apply(InvoiceIssue entity, InvoiceIssueRequest request) {
        applyService.apply(entity, request, this::nextId);
    }

    @Override
    protected InvoiceIssue saveEntity(InvoiceIssue entity) {
        return repository.save(entity);
    }

    @Override
    protected InvoiceIssueResponse toResponse(InvoiceIssue entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

}
