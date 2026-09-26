package com.leo.erp.statement.customer.service;

import com.leo.erp.common.support.ValidationMessages;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.service.CrudVisibilityPolicy;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.sales.api.SalesOrderLogisticsSourceQuery;
import com.leo.erp.sales.api.SalesOrderSourceSnapshot;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.repository.CustomerStatementSummaryAggregate;
import com.leo.erp.statement.customer.repository.CustomerStatementSummaryQueryRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementSummaryResponse;
import com.leo.erp.statement.service.StatementSettlementMutationGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class CustomerStatementService {

    private static final CrudStatusGuard<CustomerStatement> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Logger log = LoggerFactory.getLogger(CustomerStatementService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final CustomerStatementRepository repository;
    private final CustomerStatementSummaryQueryRepository summaryQueryRepository;
    private final CustomerStatementResponseAssembler responseAssembler;
    private final CustomerStatementSourceService customerStatementSourceService;
    private final CustomerStatementApplyService applyService;
    private final SalesOrderLogisticsSourceQuery salesOrderSourceQuery;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final StatementSettlementMutationGuard settlementMutationGuard;

    @Autowired
    public CustomerStatementService(CustomerStatementRepository repository,
                                    SnowflakeIdGenerator idGenerator,
                                    CustomerStatementSummaryQueryRepository summaryQueryRepository,
                                    CustomerStatementResponseAssembler responseAssembler,
                                    CustomerStatementSourceService customerStatementSourceService,
                                    CustomerStatementApplyService applyService,
                                    SalesOrderLogisticsSourceQuery salesOrderSourceQuery,
                                    SourceAllocationLockService sourceAllocationLockService,
                                    StatementSettlementMutationGuard settlementMutationGuard) {
        this.idGenerator = idGenerator;
        this.repository = repository;
        this.summaryQueryRepository = summaryQueryRepository;
        this.responseAssembler = responseAssembler;
        this.customerStatementSourceService = customerStatementSourceService;
        this.applyService = applyService;
        this.salesOrderSourceQuery = salesOrderSourceQuery;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.settlementMutationGuard = settlementMutationGuard;
    }

    @Transactional(readOnly = true)
    public Page<CustomerStatementResponse> page(PageQuery query, PageFilter filter) {
        return page(query, filter, null);
    }

    @Transactional(readOnly = true)
    public Page<CustomerStatementResponse> page(PageQuery query, PageFilter filter, String billDirection) {
        return pageEntities(query, pageSpecification(filter, normalizeBillDirection(billDirection)))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerStatementSummaryResponse summary(PageFilter filter) {
        return summary(filter, null);
    }

    @Transactional(readOnly = true)
    public CustomerStatementSummaryResponse summary(PageFilter filter, String billDirection) {
        CustomerStatementSummaryAggregate aggregate = summaryQueryRepository.summarize(
                applyDeletedVisibilityPolicy(pageSpecification(filter, normalizeBillDirection(billDirection)))
        );
        return new CustomerStatementSummaryResponse(
                aggregate.documentCount(),
                aggregate.salesAmount(),
                aggregate.receiptAmount(),
                aggregate.closingAmount()
        );
    }

    private Specification<CustomerStatement> pageSpecification(PageFilter filter) {
        return Specs.<CustomerStatement>keywordLike(filter.keyword(), "statementNo", "customerName", "projectName")
                .and(Specs.equalValueIfPresent("customerId", filter.customerId()))
                .and(Specs.equalValueIfPresent("projectId", filter.projectId()))
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.betweenIfPresent("endDate", filter.startDate(), filter.endDate()));
    }

    private Specification<CustomerStatement> pageSpecification(PageFilter filter, String billDirection) {
        return pageSpecification(filter).and(Specs.equalIfPresent("direction", billDirection));
    }

    private String normalizeBillDirection(String billDirection) {
        if (billDirection == null || billDirection.isBlank()) {
            return null;
        }
        String normalized = billDirection.trim();
        if (!StatusConstants.ALLOWED_STATEMENT_DIRECTION.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "billDirection 不合法");
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public Page<CustomerStatementCandidateResponse> candidatePage(PageQuery query, PageFilter filter) {
        return customerStatementSourceService.candidatePage(query, filter);
    }

    @Transactional(readOnly = true)
    public CustomerStatementResponse detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    @Transactional
    public CustomerStatementResponse create(CustomerStatementRequest request) {
        CustomerStatementResponse created = createStatement(
                request.audit() ? withStatus(request, StatusConstants.PENDING_CONFIRM) : request);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.CONFIRMED);
        }
        return created;
    }

    @Transactional
    public CustomerStatementResponse update(Long id, CustomerStatementRequest request) {
        CustomerStatementResponse updated = updateStatement(id,
                request.audit() ? withStatus(request, StatusConstants.PENDING_CONFIRM) : request);
        if (request.audit()) {
            return updateStatus(id, StatusConstants.CONFIRMED);
        }
        return updated;
    }

    @Transactional
    public CustomerStatementResponse updateStatus(Long id, String status) {
        CustomerStatement entity = requireEntity(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toSavedResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(allowedStatusTransitions(), currentStatus, nextStatus);
        beforeStatusUpdate(entity, currentStatus, nextStatus);
        STATUS_GUARD.writeStatus(entity, nextStatus);
        CustomerStatementResponse response = toSavedResponse(saveEntity(entity));
        log.info(
                "{} status updated: id={}, {} -> {}",
                entity.getClass().getSimpleName(),
                id,
                currentStatus,
                nextStatus
        );
        return response;
    }

    @Transactional
    public void delete(Long id) {
        CustomerStatement entity = requireEntity(id);
        STATUS_GUARD.assertDeleteAllowed(entity);
        beforeDelete(entity);
        entity.setDeletedFlag(true);
        saveEntity(entity);
        afterDelete(entity);
        log.info("{} deleted: id={}", entity.getClass().getSimpleName(), id);
    }

    /**
     * 基类 create 的显式内联：雪花 ID → 归一化 → 校验 → 应用 → 终态双写守卫 → 保存。
     */
    private CustomerStatementResponse createStatement(CustomerStatementRequest request) {
        CustomerStatement entity = newEntity();
        long entityId = idGenerator.nextId();
        assignId(entity, entityId);
        CustomerStatementRequest normalized = normalizeCreateRequest(request, entityId);
        validateCreate(normalized);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        CustomerStatementResponse response = toSavedResponse(saveCreatedEntity(entity, normalized));
        log.info("{} created: id={}", entity.getClass().getSimpleName(), entityId);
        return response;
    }

    /**
     * 基类 update 的显式内联，状态断言序列逐字保持：
     * 编辑状态守卫 → 更新校验 → 快照当前状态 → 应用请求 →
     * assertRequestStatusTransitionAllowed → allowRequestToWriteFinalStatus 分支下的
     * assertRequestDidNotWriteFinalStatus → 保存。
     */
    private CustomerStatementResponse updateStatement(Long id, CustomerStatementRequest request) {
        CustomerStatement entity = requireEntity(id);
        CustomerStatementRequest normalized = normalizeUpdateRequest(entity, request);
        STATUS_GUARD.assertEditAllowed(entity, allowProtectedStatusUpdate(entity, normalized));
        validateUpdate(entity, normalized);
        Optional<String> currentStatus = STATUS_GUARD.resolveStatus(entity);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestStatusTransitionAllowed(entity, currentStatus, allowedStatusTransitions());
        // 基类 allowRequestToWriteFinalStatus 默认 false：普通保存一律拒绝终态写入。
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        CustomerStatementResponse response = toSavedResponse(saveUpdatedEntity(entity, normalized));
        log.info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    protected CustomerStatementResponse toDetailResponse(CustomerStatement entity) {
        return responseAssembler.toDetailResponse(entity);
    }

    protected CustomerStatementResponse toSavedResponse(CustomerStatement entity) {
        return toDetailResponse(entity);
    }

    protected void validateCreate(CustomerStatementRequest request) {
        if (repository.existsByStatementNoAndDeletedFlagFalse(request.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户对账单号已存在");
        }
    }

    protected void validateUpdate(CustomerStatement entity, CustomerStatementRequest request) {
        if (!entity.getStatementNo().equals(request.statementNo())
                && repository.existsByStatementNoAndDeletedFlagFalse(request.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户对账单号已存在");
        }
    }

    protected CustomerStatementRequest normalizeCreateRequest(CustomerStatementRequest request, long entityId) {
        return new CustomerStatementRequest(
                resolveCreateBusinessNo(entityId),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.startDate(),
                request.endDate(),
                request.salesAmount(),
                request.receiptAmount(),
                request.closingAmount(),
                request.status(),
                request.remark(),
                request.items(),
                request.customerId(),
                request.audit(),
                request.direction()
        );
    }

    private CustomerStatementRequest withStatus(CustomerStatementRequest request, String status) {
        return new CustomerStatementRequest(
                request.statementNo(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.startDate(),
                request.endDate(),
                request.salesAmount(),
                request.receiptAmount(),
                request.closingAmount(),
                status,
                request.remark(),
                request.items(),
                request.customerId(),
                request.audit(),
                request.direction()
        );
    }

    protected CustomerStatementRequest normalizeUpdateRequest(CustomerStatement entity, CustomerStatementRequest request) {
        return new CustomerStatementRequest(
                entity.getStatementNo(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.startDate(),
                request.endDate(),
                request.salesAmount(),
                request.receiptAmount(),
                request.closingAmount(),
                request.status(),
                request.remark(),
                request.items(),
                request.customerId(),
                request.audit(),
                request.direction()
        );
    }

    protected CustomerStatement newEntity() {
        return new CustomerStatement();
    }

    protected void assignId(CustomerStatement entity, Long id) {
        entity.setId(id);
    }

    protected Optional<CustomerStatement> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    protected Optional<CustomerStatement> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    protected String notFoundMessage() {
        return "客户对账单不存在";
    }

    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    protected Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.STATEMENT_CONFIRM_TRANSITIONS;
    }

    private boolean allowProtectedStatusUpdate(CustomerStatement entity, CustomerStatementRequest request) {
        // 基类 allowProtectedStatusUpdate 默认 false：受保护状态单据不允许普通编辑。
        return false;
    }

    protected void beforeStatusUpdate(CustomerStatement entity, String currentStatus, String nextStatus) {
        lockSourceSalesOrders(entity, null);
        if (StatusConstants.CONFIRMED.equals(currentStatus)
                && StatusConstants.PENDING_CONFIRM.equals(nextStatus)) {
            settlementMutationGuard.assertNoSettledAllocations(
                    StatementSettlementMutationGuard.StatementType.CUSTOMER,
                    entity.getId(),
                    "反确认"
            );
        }
    }

    protected void apply(CustomerStatement entity, CustomerStatementRequest request) {
        boolean creating = entity.getStatus() == null;
        lockSourceSalesOrders(entity, request);
        if (!creating) {
            settlementMutationGuard.assertFinancialLinkageMutationAllowed(
                    StatementSettlementMutationGuard.StatementType.CUSTOMER,
                    entity.getId(),
                    customerFinancialLinkageChanged(entity, request)
            );
        }
        applyService.apply(entity, request, this::nextId);
    }

    protected void beforeDelete(CustomerStatement entity) {
        lockSourceSalesOrders(entity, null);
        settlementMutationGuard.assertNoSettledAllocations(
                StatementSettlementMutationGuard.StatementType.CUSTOMER,
                entity.getId(),
                "删除"
        );
    }

    protected void afterDelete(CustomerStatement entity) {
    }

    protected CustomerStatement saveCreatedEntity(CustomerStatement entity, CustomerStatementRequest request) {
        return saveEntity(entity);
    }

    protected CustomerStatement saveUpdatedEntity(CustomerStatement entity, CustomerStatementRequest request) {
        return saveEntity(entity);
    }

    private boolean customerFinancialLinkageChanged(CustomerStatement entity, CustomerStatementRequest request) {
        boolean identityChanged = explicitTextChanged(entity.getCustomerCode(), request.customerCode())
                || !Objects.equals(normalizeText(entity.getCustomerName()), normalizeText(request.customerName()))
                || (request.projectId() != null && !Objects.equals(entity.getProjectId(), request.projectId()))
                || !Objects.equals(normalizeText(entity.getProjectName()), normalizeText(request.projectName()));
        Set<Long> existingSourceIds = entity.getItems().stream()
                .map(CustomerStatementItem::getSourceSalesOrderItemId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> requestedSourceIds = request.items().stream()
                .map(CustomerStatementItemRequest::sourceSalesOrderItemId)
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

    private void lockSourceSalesOrders(CustomerStatement entity, CustomerStatementRequest request) {
        TreeSet<Long> sourceItemIds = new TreeSet<>();
        entity.getItems().stream()
                .map(CustomerStatementItem::getSourceSalesOrderItemId)
                .filter(Objects::nonNull)
                .forEach(sourceItemIds::add);
        if (request != null) {
            request.items().stream()
                    .map(CustomerStatementItemRequest::sourceSalesOrderItemId)
                    .filter(Objects::nonNull)
                    .forEach(sourceItemIds::add);
        }

        TreeSet<Long> sourceOrderIds = new TreeSet<>();
        if (!sourceItemIds.isEmpty()) {
            salesOrderSourceQuery.findBySourceItemIds(List.copyOf(sourceItemIds)).stream()
                    .map(SalesOrderSourceSnapshot::id)
                    .filter(Objects::nonNull)
                    .forEach(sourceOrderIds::add);
        }
        sourceAllocationLockService.lockDocumentSources(
                List.of(),
                List.copyOf(sourceOrderIds),
                List.of(),
                List.of()
        );
    }

    protected CustomerStatement saveEntity(CustomerStatement entity) {
        return repository.save(entity);
    }

    protected CustomerStatementResponse toResponse(CustomerStatement entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

    private CustomerStatement requireEntity(Long id) {
        return findActiveEntity(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    private CustomerStatement requireDetailEntity(Long id) {
        if (allowViewingDeletedRecords()) {
            return findVisibleEntity(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        }
        return requireEntity(id);
    }

    private Specification<CustomerStatement> applyDeletedVisibilityPolicy(Specification<CustomerStatement> specification) {
        return VISIBILITY_POLICY.applyDeletedVisibility(specification, allowViewingDeletedRecords());
    }

    private Page<CustomerStatement> pageEntities(PageQuery query, Specification<CustomerStatement> specification) {
        return repository.findAll(applyDeletedVisibilityPolicy(specification), query.toPageable("id"));
    }

    private long nextId() {
        return idGenerator.nextId();
    }

    private String resolveCreateBusinessNo(Long entityId) {
        if (entityId == null || entityId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.SNOWFLAKE_ID_NOT_ASSIGNED);
        }
        return String.valueOf(entityId);
    }
}
