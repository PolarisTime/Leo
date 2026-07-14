package com.leo.erp.statement.supplier.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatementItem;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementCandidateResponse;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
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
public class SupplierStatementService extends AbstractCrudService<SupplierStatement, SupplierStatementRequest, SupplierStatementResponse> {

    private final SupplierStatementRepository repository;
    private final SupplierStatementResponseAssembler responseAssembler;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final SupplierStatementSourceService supplierStatementSourceService;
    private final SupplierStatementApplyService applyService;
    private final PurchaseInboundItemQueryService purchaseInboundItemQueryService;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final StatementSettlementMutationGuard settlementMutationGuard;

    @Autowired
    public SupplierStatementService(SupplierStatementRepository repository,
                                    SnowflakeIdGenerator idGenerator,
                                    SupplierStatementResponseAssembler responseAssembler,
                                    WorkflowTransitionGuard workflowTransitionGuard,
                                    SupplierStatementSourceService supplierStatementSourceService,
                                    SupplierStatementApplyService applyService,
                                    PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                    SourceAllocationLockService sourceAllocationLockService,
                                    StatementSettlementMutationGuard settlementMutationGuard) {
        super(idGenerator);
        this.repository = repository;
        this.responseAssembler = responseAssembler;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.supplierStatementSourceService = supplierStatementSourceService;
        this.applyService = applyService;
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.settlementMutationGuard = settlementMutationGuard;
    }

    @Transactional(readOnly = true)
    public Page<SupplierStatementResponse> page(PageQuery query, PageFilter filter) {
        Specification<SupplierStatement> spec = Specs.<SupplierStatement>keywordLike(filter.keyword(), "statementNo", "supplierName")
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("endDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    private static final String[] SUPPLIER_STATEMENT_SEARCH_FIELDS = {
            "statementNo",
            "supplierName"
    };

    @Transactional(readOnly = true)
    public List<SupplierStatementResponse> search(String keyword, int maxSize) {
        return search(keyword, SUPPLIER_STATEMENT_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Transactional(readOnly = true)
    public Page<SupplierStatementCandidateResponse> candidatePage(PageQuery query, PageFilter filter) {
        return supplierStatementSourceService.candidatePage(query, filter);
    }

    @Override
    protected SupplierStatementResponse toDetailResponse(SupplierStatement entity) {
        return responseAssembler.toDetailResponse(entity);
    }

    @Override
    protected SupplierStatementResponse toSavedResponse(SupplierStatement entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void validateCreate(SupplierStatementRequest request) {
        if (repository.existsByStatementNoAndDeletedFlagFalse(request.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商对账单号已存在");
        }
    }

    @Override
    protected void validateUpdate(SupplierStatement entity, SupplierStatementRequest request) {
        if (!entity.getStatementNo().equals(request.statementNo())
                && repository.existsByStatementNoAndDeletedFlagFalse(request.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商对账单号已存在");
        }
    }

    @Override
    protected SupplierStatementRequest normalizeCreateRequest(SupplierStatementRequest request, long entityId) {
        return new SupplierStatementRequest(
                resolveCreateBusinessNo("supplier-statement", request.statementNo(), entityId),
                request.supplierCode(),
                request.supplierName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.startDate(),
                request.endDate(),
                request.purchaseAmount(),
                request.paymentAmount(),
                request.closingAmount(),
                request.status(),
                request.remark(),
                request.items(),
                request.supplierId()
        );
    }

    @Override
    protected SupplierStatementRequest normalizeUpdateRequest(SupplierStatement entity, SupplierStatementRequest request) {
        return new SupplierStatementRequest(
                entity.getStatementNo(),
                request.supplierCode(),
                request.supplierName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.startDate(),
                request.endDate(),
                request.purchaseAmount(),
                request.paymentAmount(),
                request.closingAmount(),
                request.status(),
                request.remark(),
                request.items(),
                request.supplierId()
        );
    }

    @Override
    protected SupplierStatement newEntity() {
        return new SupplierStatement();
    }

    @Override
    protected void assignId(SupplierStatement entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<SupplierStatement> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<SupplierStatement> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "供应商对账单不存在";
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
    protected void beforeStatusUpdate(SupplierStatement entity, String currentStatus, String nextStatus) {
        lockSourcePurchaseInbounds(entity, null);
        if (StatusConstants.CONFIRMED.equals(currentStatus)
                && StatusConstants.PENDING_CONFIRM.equals(nextStatus)) {
            settlementMutationGuard.assertNoSettledAllocations(
                    StatementSettlementMutationGuard.StatementType.SUPPLIER,
                    entity.getId(),
                    "反确认"
            );
        }
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "supplier-statement",
                currentStatus,
                nextStatus,
                StatusConstants.CONFIRMED
        );
    }

    @Override
    protected void apply(SupplierStatement entity, SupplierStatementRequest request) {
        lockSourcePurchaseInbounds(entity, request);
        if (entity.getId() != null) {
            settlementMutationGuard.assertFinancialLinkageMutationAllowed(
                    StatementSettlementMutationGuard.StatementType.SUPPLIER,
                    entity.getId(),
                    supplierFinancialLinkageChanged(entity, request)
            );
        }
        applyService.apply(entity, request, this::nextId);
    }

    @Override
    protected void beforeDelete(SupplierStatement entity) {
        lockSourcePurchaseInbounds(entity, null);
        settlementMutationGuard.assertNoSettledAllocations(
                StatementSettlementMutationGuard.StatementType.SUPPLIER,
                entity.getId(),
                "删除"
        );
    }

    private boolean supplierFinancialLinkageChanged(SupplierStatement entity, SupplierStatementRequest request) {
        boolean identityChanged = explicitTextChanged(entity.getSupplierCode(), request.supplierCode())
                || !Objects.equals(normalizeText(entity.getSupplierName()), normalizeText(request.supplierName()))
                || (request.settlementCompanyId() != null
                && !Objects.equals(entity.getSettlementCompanyId(), request.settlementCompanyId()));
        Set<Long> existingSourceIds = entity.getItems().stream()
                .map(SupplierStatementItem::getSourceInboundItemId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> requestedSourceIds = request.items().stream()
                .map(SupplierStatementItemRequest::sourceInboundItemId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return identityChanged || !existingSourceIds.equals(requestedSourceIds);
    }

    private boolean explicitTextChanged(String currentValue, String requestedValue) {
        String normalizedRequested = normalizeText(requestedValue);
        return normalizedRequested != null
                && !Objects.equals(normalizeText(currentValue), normalizedRequested);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void lockSourcePurchaseInbounds(SupplierStatement entity, SupplierStatementRequest request) {
        TreeSet<Long> sourceItemIds = new TreeSet<>();
        entity.getItems().stream()
                .map(SupplierStatementItem::getSourceInboundItemId)
                .filter(Objects::nonNull)
                .forEach(sourceItemIds::add);
        if (request != null) {
            request.items().stream()
                    .map(SupplierStatementItemRequest::sourceInboundItemId)
                    .filter(Objects::nonNull)
                    .forEach(sourceItemIds::add);
        }

        TreeSet<Long> sourceInboundIds = new TreeSet<>();
        if (!sourceItemIds.isEmpty()) {
            purchaseInboundItemQueryService.findAllActiveByIdIn(List.copyOf(sourceItemIds)).stream()
                    .map(PurchaseInboundItem::getPurchaseInbound)
                    .filter(Objects::nonNull)
                    .map(PurchaseInbound::getId)
                    .filter(Objects::nonNull)
                    .forEach(sourceInboundIds::add);
        }
        sourceAllocationLockService.lockDocumentSources(
                List.copyOf(sourceInboundIds),
                List.of(),
                List.of(),
                List.of()
        );
    }

    @Override
    protected SupplierStatement saveEntity(SupplierStatement entity) {
        return repository.save(entity);
    }

    @Override
    protected SupplierStatementResponse toResponse(SupplierStatement entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

}
