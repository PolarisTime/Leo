package com.leo.erp.statement.freight.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractStatusCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapper;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.repository.FreightStatementSummaryAggregate;
import com.leo.erp.statement.freight.repository.FreightStatementSummaryQueryRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class FreightStatementService extends AbstractStatusCrudService<
        FreightStatement, FreightStatementCommand, FreightStatementView> {

    private static final String[] FREIGHT_STATEMENT_SEARCH_FIELDS = {
            "statementNo",
            "carrierCode",
            "carrierName"
    };

    private final FreightStatementRepository repository;
    private final FreightStatementSummaryQueryRepository summaryQueryRepository;
    private final FreightStatementWebMapper freightStatementWebMapper;
    private final FreightStatementSourceService freightStatementSourceService;
    private final FreightStatementViewAssembler viewAssembler;
    private final FreightStatementPageAssembler pageAssembler;
    private final FreightStatementWorkflowService workflowService;

    public FreightStatementService(FreightStatementRepository repository,
                                   FreightStatementSummaryQueryRepository summaryQueryRepository,
                                   SnowflakeIdGenerator idGenerator,
                                   FreightStatementWebMapper freightStatementWebMapper,
                                   FreightStatementSourceService freightStatementSourceService,
                                   FreightStatementViewAssembler viewAssembler,
                                   FreightStatementPageAssembler pageAssembler,
                                   FreightStatementWorkflowService workflowService) {
        super(idGenerator);
        this.repository = repository;
        this.summaryQueryRepository = summaryQueryRepository;
        this.freightStatementWebMapper = freightStatementWebMapper;
        this.freightStatementSourceService = freightStatementSourceService;
        this.viewAssembler = viewAssembler;
        this.pageAssembler = pageAssembler;
        this.workflowService = workflowService;
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementView> page(PageQuery query, PageFilter filter) {
        return page(query, filter, null);
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementView> page(PageQuery query, PageFilter filter, String carrierCode) {
        Specification<FreightStatement> spec = buildPageSpecification(filter, carrierCode);
        Page<FreightStatement> entityPage = repository.findAll(spec, query.toPageable("id"));
        return pageAssembler.toViewPage(entityPage);
    }

    @Transactional(readOnly = true)
    public FreightStatementSummaryResponse summary(PageFilter filter, String carrierCode) {
        FreightStatementSummaryAggregate aggregate = summaryQueryRepository.summarize(
                buildPageSpecification(filter, carrierCode)
        );
        return new FreightStatementSummaryResponse(
                aggregate.documentCount(),
                aggregate.totalWeight(),
                aggregate.totalFreight(),
                aggregate.paidAmount(),
                aggregate.unpaidAmount()
        );
    }

    private Specification<FreightStatement> buildPageSpecification(PageFilter filter, String carrierCode) {
        return applyDeletedVisibilityPolicy(
                Specs.<FreightStatement>keywordLike(filter.keyword(), "statementNo", "carrierCode", "carrierName")
                        .and(Specs.equalValueIfPresent("carrierId", filter.carrierId()))
                        .and(Specs.equalIfPresent("carrierCode", carrierCode))
                        .and(Specs.equalIfPresent("carrierName", filter.name()))
                        .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                        .and(Specs.documentStatus(filter.status()))
                        .and(Specs.betweenIfPresent("endDate", filter.startDate(), filter.endDate()))
        );
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementResponse> responsePage(PageQuery query, PageFilter filter) {
        return page(query, filter).map(freightStatementWebMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementResponse> responsePage(PageQuery query, PageFilter filter, String carrierCode) {
        return page(query, filter, carrierCode).map(freightStatementWebMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<FreightStatementView> search(String keyword, int maxSize) {
        return search(keyword, FREIGHT_STATEMENT_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Transactional(readOnly = true)
    public List<FreightStatementResponse> responseSearch(String keyword, int maxSize) {
        return search(keyword, maxSize).stream()
                .map(freightStatementWebMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FreightStatementResponse responseDetail(Long id) {
        return freightStatementWebMapper.toResponse(detail(id));
    }

    @Transactional
    public FreightStatementResponse responseCreate(FreightStatementRequest request) {
        return freightStatementWebMapper.toResponse(create(freightStatementWebMapper.toCommand(request)));
    }

    @Transactional
    public FreightStatementResponse responseUpdate(Long id, FreightStatementRequest request) {
        return freightStatementWebMapper.toResponse(update(id, freightStatementWebMapper.toCommand(request)));
    }

    @Transactional
    public FreightStatementResponse responseUpdateStatus(Long id, String status) {
        return freightStatementWebMapper.toResponse(updateStatus(id, status));
    }

    @Override
    @Transactional
    public FreightStatementView create(FreightStatementCommand command) {
        FreightStatementView created = super.create(
                command.audit() ? withStatus(command, StatusConstants.DRAFT) : command);
        if (command.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Override
    @Transactional
    public FreightStatementView update(Long id, FreightStatementCommand command) {
        FreightStatementView updated = super.update(id,
                command.audit() ? withStatus(command, StatusConstants.DRAFT) : command);
        if (command.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementCandidateResponse> candidatePage(PageQuery query, PageFilter filter) {
        return freightStatementSourceService.candidatePage(query, filter);
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementCandidateResponse> candidatePage(PageQuery query,
                                                                 PageFilter filter,
                                                                 String carrierCode) {
        return freightStatementSourceService.candidatePage(query, filter, carrierCode);
    }

    @Override
    protected FreightStatementView toDetailResponse(FreightStatement entity) {
        return viewAssembler.toDetailView(entity);
    }

    @Override
    protected FreightStatementView toSavedResponse(FreightStatement entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void validateCreate(FreightStatementCommand command) {
        if (repository.existsByStatementNoAndDeletedFlagFalse(command.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流对账单号已存在");
        }
        String status = normalizeText(command.status());
        if (status != null && !StatusConstants.DRAFT.equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新物流对账单只能保存为草稿");
        }
    }

    @Override
    protected void validateUpdate(FreightStatement entity, FreightStatementCommand command) {
        if (!entity.getStatementNo().equals(command.statementNo())
                && repository.existsByStatementNoAndDeletedFlagFalse(command.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流对账单号已存在");
        }
    }

    @Override
    protected FreightStatementCommand normalizeCreateRequest(FreightStatementCommand command, long entityId) {
        return new FreightStatementCommand(
                resolveCreateBusinessNo(entityId),
                command.carrierCode(),
                command.carrierName(),
                command.settlementCompanyId(),
                command.settlementCompanyName(),
                command.startDate(),
                command.endDate(),
                command.totalWeight(),
                command.totalFreight(),
                command.paidAmount(),
                command.unpaidAmount(),
                command.status(),
                command.attachment(),
                command.remark(),
                command.items(),
                command.carrierId(),
                command.audit()
        );
    }

    @Override
    protected FreightStatementCommand normalizeUpdateRequest(FreightStatement entity, FreightStatementCommand command) {
        String requestedStatus = normalizeText(command.status());
        if (requestedStatus != null && !Objects.equals(entity.getStatus(), requestedStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流对账单状态只能通过审核或反审核操作变更");
        }
        return new FreightStatementCommand(
                entity.getStatementNo(),
                command.carrierCode(),
                command.carrierName(),
                command.settlementCompanyId(),
                command.settlementCompanyName(),
                command.startDate(),
                command.endDate(),
                command.totalWeight(),
                command.totalFreight(),
                command.paidAmount(),
                command.unpaidAmount(),
                command.status(),
                command.attachment(),
                command.remark(),
                command.items(),
                command.carrierId(),
                command.audit()
        );
    }

    private FreightStatementCommand withStatus(FreightStatementCommand command, String status) {
        return new FreightStatementCommand(
                command.statementNo(), command.carrierCode(), command.carrierName(),
                command.settlementCompanyId(), command.settlementCompanyName(), command.startDate(), command.endDate(),
                command.totalWeight(), command.totalFreight(), command.paidAmount(), command.unpaidAmount(), status,
                command.attachment(), command.remark(), command.items(), command.carrierId(), command.audit()
        );
    }

    @Override
    protected FreightStatement newEntity() {
        return new FreightStatement();
    }

    @Override
    protected void assignId(FreightStatement entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<FreightStatement> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<FreightStatement> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "物流对账单不存在";
    }

    @Override
    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    @Override
    protected Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.DRAFT_AUDIT_TRANSITIONS;
    }

    @Override
    protected void apply(FreightStatement entity, FreightStatementCommand command) {
        workflowService.apply(entity, command, this::nextId);
    }

    @Override
    protected void beforeStatusUpdate(FreightStatement entity, String currentStatus, String nextStatus) {
        workflowService.beforeStatusUpdate(entity, currentStatus, nextStatus);
    }

    @Override
    @Transactional
    public FreightStatementView updateStatus(Long id, String status) {
        FreightStatement statement = requireEntity(id);
        String currentStatus = statement.getStatus();
        FreightStatementView response = super.updateStatus(id, status);
        if (!Objects.equals(currentStatus, response.status())) {
            workflowService.publishStatusChanged(statement, currentStatus, response.status());
        }
        return response;
    }

    @Override
    protected void beforeDelete(FreightStatement entity) {
        workflowService.assertDeleteAllowed(entity);
    }

    @Override
    protected void afterDelete(FreightStatement entity) {
        workflowService.publishDeleted(entity);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    protected FreightStatement saveEntity(FreightStatement entity) {
        return workflowService.save(entity);
    }

    @Override
    protected FreightStatement saveCreatedEntity(FreightStatement entity, FreightStatementCommand command) {
        return workflowService.saveCreated(entity, command);
    }

    @Override
    protected FreightStatement saveUpdatedEntity(FreightStatement entity, FreightStatementCommand command) {
        return workflowService.saveUpdated(entity, command);
    }

    @Override
    protected FreightStatementView toResponse(FreightStatement entity) {
        return viewAssembler.toDetailView(entity);
    }
}
