package com.leo.erp.statement.freight.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapper;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import com.leo.erp.statement.service.StatementSettlementMutationGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
public class FreightStatementService extends AbstractCrudService<FreightStatement, FreightStatementCommand, FreightStatementView> {

    private final FreightStatementRepository repository;
    private final StatementSettlementSyncService statementSettlementSyncService;
    private final FreightStatementWebMapper freightStatementWebMapper;
    private final FreightStatementSourceService freightStatementSourceService;
    private final FreightStatementViewAssembler viewAssembler;
    private final FreightStatementPageAssembler pageAssembler;
    private final FreightStatementApplyService freightStatementApplyService;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final StatementSettlementMutationGuard settlementMutationGuard;

    @Autowired
    public FreightStatementService(FreightStatementRepository repository,
                                   SnowflakeIdGenerator idGenerator,
                                   StatementSettlementSyncService statementSettlementSyncService,
                                   FreightStatementWebMapper freightStatementWebMapper,
                                   FreightStatementSourceService freightStatementSourceService,
                                   FreightStatementViewAssembler viewAssembler,
                                   FreightStatementPageAssembler pageAssembler,
                                   FreightStatementApplyService freightStatementApplyService,
                                   SourceAllocationLockService sourceAllocationLockService,
                                   StatementSettlementMutationGuard settlementMutationGuard) {
        super(idGenerator);
        this.repository = repository;
        this.statementSettlementSyncService = statementSettlementSyncService;
        this.freightStatementWebMapper = freightStatementWebMapper;
        this.freightStatementSourceService = freightStatementSourceService;
        this.viewAssembler = viewAssembler;
        this.pageAssembler = pageAssembler;
        this.freightStatementApplyService = freightStatementApplyService;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.settlementMutationGuard = settlementMutationGuard;
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementView> page(PageQuery query, PageFilter filter) {
        return page(query, filter, null);
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementView> page(PageQuery query, PageFilter filter, String carrierCode) {
        Specification<FreightStatement> spec = applyDeletedVisibilityPolicy(
                Specs.<FreightStatement>keywordLike(filter.keyword(), "statementNo", "carrierCode", "carrierName")
                .and(Specs.equalValueIfPresent("carrierId", filter.carrierId()))
                .and(Specs.equalIfPresent("carrierCode", carrierCode))
                .and(Specs.equalIfPresent("carrierName", filter.name()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.equalIfPresent("signStatus", filter.signStatus()))
                .and(Specs.betweenIfPresent("endDate", filter.startDate(), filter.endDate()))
        );
        Page<FreightStatement> entityPage = repository.findAll(DataScopeContext.apply(spec), query.toPageable("id"));
        return pageAssembler.toViewPage(entityPage);
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementResponse> responsePage(PageQuery query, PageFilter filter) {
        return page(query, filter).map(freightStatementWebMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementResponse> responsePage(PageQuery query, PageFilter filter, String carrierCode) {
        return page(query, filter, carrierCode).map(freightStatementWebMapper::toResponse);
    }

    private static final String[] FREIGHT_STATEMENT_SEARCH_FIELDS = {
            "statementNo",
            "carrierCode",
            "carrierName"
    };

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
                resolveCreateBusinessNo("freight-statement", command.statementNo(), entityId),
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
                command.signStatus(),
                command.attachment(),
                command.remark(),
                command.items(),
                command.carrierId()
        );
    }

    @Override
    protected FreightStatementCommand normalizeUpdateRequest(FreightStatement entity, FreightStatementCommand command) {
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
                command.signStatus(),
                command.attachment(),
                command.remark(),
                command.items(),
                command.carrierId()
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
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return java.util.Set.of(
                StatusConstants.PENDING_AUDIT + "->" + StatusConstants.AUDITED,
                StatusConstants.AUDITED + "->" + StatusConstants.PENDING_AUDIT
        );
    }

    @Override
    protected void apply(FreightStatement entity, FreightStatementCommand command) {
        lockSourceFreightBills(entity, command);
        if (entity.getId() != null) {
            settlementMutationGuard.assertFinancialLinkageMutationAllowed(
                    StatementSettlementMutationGuard.StatementType.FREIGHT,
                    entity.getId(),
                    freightFinancialLinkageChanged(entity, command)
            );
        }
        freightStatementApplyService.apply(entity, command, this::nextId);
    }

    @Override
    protected void beforeStatusUpdate(FreightStatement entity, String currentStatus, String nextStatus) {
        lockSourceFreightBills(entity, null);
        if (StatusConstants.AUDITED.equals(currentStatus)
                && StatusConstants.PENDING_AUDIT.equals(nextStatus)) {
            settlementMutationGuard.assertNoSettledAllocations(
                    StatementSettlementMutationGuard.StatementType.FREIGHT,
                    entity.getId(),
                    "反审核"
            );
        }
    }

    @Override
    protected void beforeDelete(FreightStatement entity) {
        lockSourceFreightBills(entity, null);
        settlementMutationGuard.assertNoSettledAllocations(
                StatementSettlementMutationGuard.StatementType.FREIGHT,
                entity.getId(),
                "删除"
        );
    }

    private boolean freightFinancialLinkageChanged(FreightStatement entity, FreightStatementCommand command) {
        boolean identityChanged = (command.carrierId() != null
                && !Objects.equals(entity.getCarrierId(), command.carrierId()))
                || explicitTextChanged(entity.getCarrierCode(), command.carrierCode())
                || !Objects.equals(normalizeText(entity.getCarrierName()), normalizeText(command.carrierName()))
                || (command.settlementCompanyId() != null
                && !Objects.equals(entity.getSettlementCompanyId(), command.settlementCompanyId()));
        Set<Long> existingSources = entity.getItems().stream()
                .map(FreightStatementItem::getSourceFreightBillId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> requestedSources = command.items().stream()
                .map(FreightStatementItemCommand::sourceFreightBillId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return identityChanged || !existingSources.equals(requestedSources);
    }

    private boolean explicitTextChanged(String currentValue, String requestedValue) {
        String normalizedRequested = normalizeText(requestedValue);
        return normalizedRequested != null
                && !Objects.equals(normalizeText(currentValue), normalizedRequested);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void lockSourceFreightBills(FreightStatement entity, FreightStatementCommand command) {
        TreeSet<Long> sourceIds = new TreeSet<>();
        entity.getItems().stream()
                .map(FreightStatementItem::getSourceFreightBillId)
                .filter(Objects::nonNull)
                .forEach(sourceIds::add);
        if (command != null) {
            command.items().stream()
                    .map(FreightStatementItemCommand::sourceFreightBillId)
                    .filter(Objects::nonNull)
                    .forEach(sourceIds::add);
        }
        sourceAllocationLockService.lockDocumentSources(
                List.of(),
                List.of(),
                List.of(),
                List.copyOf(sourceIds)
        );
    }

    @Override
    protected FreightStatement saveEntity(FreightStatement entity) {
        FreightStatement saved = repository.save(entity);
        return statementSettlementSyncService.syncFreightStatement(saved);
    }

    @Override
    protected FreightStatementView toResponse(FreightStatement entity) {
        return viewAssembler.toDetailView(entity);
    }
}
