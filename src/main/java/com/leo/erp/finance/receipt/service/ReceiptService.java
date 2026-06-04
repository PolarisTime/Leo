package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import com.leo.erp.finance.receipt.mapper.ReceiptMapper;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.finance.receipt.repository.ReceiptRepository;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationResponse;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.service.CustomerStatementQueryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ReceiptService extends AbstractCrudService<Receipt, ReceiptRequest, ReceiptResponse> {

    private static final String RECEIPT_STATUS_SETTLED = StatusConstants.RECEIVED;

    private final ReceiptRepository receiptRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final ReceiptMapper receiptMapper;
    private final CustomerStatementQueryService customerStatementQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public ReceiptService(ReceiptRepository receiptRepository,
                          ReceiptAllocationRepository receiptAllocationRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          ReceiptMapper receiptMapper,
                          CustomerStatementQueryService customerStatementQueryService,
                          ApplicationEventPublisher eventPublisher,
                          ResourceRecordAccessGuard resourceRecordAccessGuard,
                          WorkflowTransitionGuard workflowTransitionGuard) {
        super(snowflakeIdGenerator);
        this.receiptRepository = receiptRepository;
        this.receiptAllocationRepository = receiptAllocationRepository;
        this.receiptMapper = receiptMapper;
        this.customerStatementQueryService = customerStatementQueryService;
        this.eventPublisher = eventPublisher;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    public Page<ReceiptResponse> page(PageQuery query, PageFilter filter) {
        Specification<Receipt> spec = Specs.<Receipt>keywordLike(filter.keyword(), "receiptNo", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("receiptDate", filter.startDate(), filter.endDate()));
        return page(query, spec, receiptRepository);
    }

    private static final String[] RECEIPT_SEARCH_FIELDS = {
            "receiptNo",
            "customerName",
            "projectName"
    };

    public List<ReceiptResponse> search(String keyword, int maxSize) {
        return search(keyword, RECEIPT_SEARCH_FIELDS, maxSize, null, receiptRepository);
    }

    @Override
    protected void validateCreate(ReceiptRequest request) {
        ensureReceiptNoUnique(request.receiptNo());
    }

    @Override
    protected void validateUpdate(Receipt entity, ReceiptRequest request) {
        if (!entity.getReceiptNo().equals(request.receiptNo())) {
            ensureReceiptNoUnique(request.receiptNo());
        }
    }

    @Override
    protected ReceiptRequest normalizeCreateRequest(ReceiptRequest request, long entityId) {
        return new ReceiptRequest(
                resolveCreateBusinessNo("receipt", request.receiptNo(), entityId),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.sourceStatementId(),
                request.receiptDate(),
                request.payType(),
                request.amount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected ReceiptRequest normalizeUpdateRequest(Receipt entity, ReceiptRequest request) {
        return new ReceiptRequest(
                entity.getReceiptNo(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.sourceStatementId(),
                request.receiptDate(),
                request.payType(),
                request.amount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected Receipt newEntity() {
        return new Receipt();
    }

    @Override
    protected void assignId(Receipt entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<Receipt> findActiveEntity(Long id) {
        return receiptRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<Receipt> findVisibleEntity(Long id) {
        return receiptRepository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "收款单不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return java.util.Set.of(
                StatusConstants.DRAFT + "->" + StatusConstants.RECEIVED,
                StatusConstants.RECEIVED + "->" + StatusConstants.DRAFT
        );
    }

    @Override
    protected void beforeStatusUpdate(Receipt entity, String currentStatus, String nextStatus) {
        captureOriginalAllocationStatementIds(entity);
        if (!RECEIPT_STATUS_SETTLED.equals(nextStatus)) {
            return;
        }
        ReceiptRequest request = toStatusOnlyRequest(entity);
        assertSettlementAllocationsComplete(
                nextStatus,
                entity.getItems().isEmpty(),
                totalAllocatedAmount(entity.getItems()),
                entity.getAmount()
        );
        Map<Long, BigDecimal> requestAllocatedAmountMap = new HashMap<>();
        String resolvedCustomerCode = null;
        for (int i = 0; i < entity.getItems().size(); i++) {
            ReceiptAllocation item = entity.getItems().get(i);
            CustomerStatement statement = requireAccessibleCustomerStatement(item.getSourceStatementId());
            resolvedCustomerCode = mergeCustomerCode(resolvedCustomerCode, statement.getCustomerCode());
            validateLinkedCustomerStatement(
                    request,
                    nextStatus,
                    entity.getId(),
                    statement,
                    TradeItemCalculator.safeBigDecimal(item.getAllocatedAmount()),
                    requestAllocatedAmountMap,
                    i + 1
            );
        }
        entity.setCustomerCode(mergeCustomerCode(entity.getCustomerCode(), resolvedCustomerCode));
    }

    @Override
    protected ReceiptResponse toDetailResponse(Receipt entity) {
        ReceiptResponse response = receiptMapper.toResponse(entity);
        return new ReceiptResponse(
                response.id(),
                response.receiptNo(),
                response.customerCode(),
                response.customerName(),
                response.projectId(),
                response.projectName(),
                response.sourceStatementId(),
                response.receiptDate(),
                response.payType(),
                response.amount(),
                response.status(),
                response.operatorName(),
                response.remark(),
                entity.getItems().stream().map(this::toAllocationResponse).toList()
        );
    }

    @Override
    protected ReceiptResponse toSavedResponse(Receipt entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void apply(Receipt entity, ReceiptRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "收款单状态",
                StatusConstants.ALLOWED_RECEIPT_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "receipt",
                entity.getStatus(),
                nextStatus,
                RECEIPT_STATUS_SETTLED
        );
        captureOriginalAllocationStatementIds(entity);
        entity.setReceiptNo(request.receiptNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setCustomerCode(trimToNull(request.customerCode()));
        entity.setProjectId(request.projectId());
        entity.setReceiptDate(request.receiptDate());
        entity.setPayType(request.payType());
        entity.setAmount(TradeItemCalculator.scaleAmount(request.amount()));
        entity.setStatus(nextStatus);
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());
        entity.setCustomerCode(mergeCustomerCode(entity.getCustomerCode(), applyAllocations(entity, request, nextStatus)));
        entity.setSourceStatementId(resolveLegacySourceStatementId(entity.getItems()));
    }

    @Override
    protected Receipt saveEntity(Receipt entity) {
        Receipt saved = receiptRepository.save(entity);
        syncCustomerStatements(buildAffectedStatementIds(saved));
        return saved;
    }

    @Override
    protected ReceiptResponse toResponse(Receipt entity) {
        return receiptMapper.toResponse(entity);
    }

    private void ensureReceiptNoUnique(String receiptNo) {
        if (receiptRepository.existsByReceiptNoAndDeletedFlagFalse(receiptNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "收款单号已存在");
        }
    }

    private String applyAllocations(Receipt entity, ReceiptRequest request, String nextStatus) {
        List<ReceiptAllocationRequest> allocationRequests = normalizeAllocationRequests(request);
        String resolvedCustomerCode = null;
        BigDecimal totalAllocatedAmount = BigDecimal.ZERO;
        Map<Long, CustomerStatement> statementMap = new HashMap<>();
        Map<Long, BigDecimal> requestAllocatedAmountMap = new HashMap<>();
        List<ReceiptAllocation> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                allocationRequests,
                ReceiptAllocation::getId,
                ReceiptAllocationRequest::id,
                ReceiptAllocation::new,
                this::nextId,
                ReceiptAllocation::setId
        );

        for (int i = 0; i < allocationRequests.size(); i++) {
            ReceiptAllocationRequest source = allocationRequests.get(i);
            BigDecimal allocatedAmount = normalizeAllocatedAmount(source.allocatedAmount(), i + 1);
            CustomerStatement statement = statementMap.computeIfAbsent(
                    source.sourceStatementId(),
                    this::requireAccessibleCustomerStatement
            );
            resolvedCustomerCode = mergeCustomerCode(resolvedCustomerCode, statement.getCustomerCode());
            validateLinkedCustomerStatement(
                    request,
                    nextStatus,
                    entity.getId(),
                    statement,
                    allocatedAmount,
                    requestAllocatedAmountMap,
                    i + 1
            );

            ReceiptAllocation item = items.get(i);
            item.setReceipt(entity);
            item.setLineNo(i + 1);
            item.setSourceStatementId(statement.getId());
            item.setAllocatedAmount(allocatedAmount);
            totalAllocatedAmount = totalAllocatedAmount.add(allocatedAmount);
        }

        if (totalAllocatedAmount.compareTo(TradeItemCalculator.safeBigDecimal(entity.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "核销金额合计不能超过收款金额");
        }
        assertSettlementAllocationsComplete(nextStatus, allocationRequests.isEmpty(), totalAllocatedAmount, entity.getAmount());
        entity.getItems().sort(java.util.Comparator.comparing(ReceiptAllocation::getLineNo));
        return resolvedCustomerCode;
    }

    private void assertSettlementAllocationsComplete(String nextStatus,
                                                     boolean allocationEmpty,
                                                     BigDecimal totalAllocatedAmount,
                                                     BigDecimal receiptAmount) {
        if (!RECEIPT_STATUS_SETTLED.equals(nextStatus)) {
            return;
        }
        if (allocationEmpty) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已收款状态必须填写核销明细");
        }
        if (totalAllocatedAmount.compareTo(TradeItemCalculator.safeBigDecimal(receiptAmount)) != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "收款金额必须等于核销金额合计");
        }
    }

    private BigDecimal totalAllocatedAmount(List<ReceiptAllocation> items) {
        return items.stream()
                .map(ReceiptAllocation::getAllocatedAmount)
                .map(TradeItemCalculator::safeBigDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<ReceiptAllocationRequest> normalizeAllocationRequests(ReceiptRequest request) {
        if (request.items() != null && !request.items().isEmpty()) {
            return request.items();
        }
        if (request.sourceStatementId() == null) {
            return List.of();
        }
        return List.of(new ReceiptAllocationRequest(null, request.sourceStatementId(), request.amount()));
    }

    private ReceiptRequest toStatusOnlyRequest(Receipt entity) {
        return new ReceiptRequest(
                entity.getReceiptNo(),
                entity.getCustomerCode(),
                entity.getCustomerName(),
                entity.getProjectId(),
                entity.getProjectName(),
                entity.getSourceStatementId(),
                entity.getReceiptDate(),
                entity.getPayType(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getOperatorName(),
                entity.getRemark(),
                entity.getItems().stream()
                        .map(item -> new ReceiptAllocationRequest(
                                item.getId(),
                                item.getSourceStatementId(),
                                item.getAllocatedAmount()
                        ))
                        .toList()
        );
    }

    private CustomerStatement requireAccessibleCustomerStatement(Long statementId) {
        CustomerStatement statement = customerStatementQueryService.requireActiveById(statementId);
        resourceRecordAccessGuard.assertCurrentUserCanAccess(
                "customer-statement",
                ResourcePermissionCatalog.READ,
                statement
        );
        return statement;
    }

    private void validateLinkedCustomerStatement(ReceiptRequest request,
                                                 String normalizedStatus,
                                                 Long currentReceiptId,
                                                 CustomerStatement statement,
                                                 BigDecimal allocatedAmount,
                                                 Map<Long, BigDecimal> requestAllocatedAmountMap,
                                                 int lineNo) {
        if (!statement.getCustomerName().equals(request.customerName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行对账单客户与收款单客户不一致");
        }
        if (!statement.getProjectName().equals(request.projectName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行对账单项目与收款单项目不一致");
        }
        validateCustomerCode(request.customerCode(), statement.getCustomerCode(), lineNo);
        if (requestAllocatedAmountMap.containsKey(statement.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一收款单不能重复核销同一客户对账单");
        }
        if (RECEIPT_STATUS_SETTLED.equals(normalizedStatus)) {
            BigDecimal settledAmount = TradeItemCalculator.safeBigDecimal(
                    receiptAllocationRepository.sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(
                            statement.getId(),
                            RECEIPT_STATUS_SETTLED,
                            currentReceiptId
                    )
            );
            BigDecimal nextSettledAmount = settledAmount.add(allocatedAmount);
            if (nextSettledAmount.compareTo(statement.getSalesAmount()) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行关联客户对账单累计收款金额不能超过销售金额");
            }
        }
        requestAllocatedAmountMap.put(statement.getId(), allocatedAmount);
    }

    private void validateCustomerCode(String requestCustomerCode, String statementCustomerCode, int lineNo) {
        String normalizedRequestCode = trimToNull(requestCustomerCode);
        String normalizedStatementCode = trimToNull(statementCustomerCode);
        if (normalizedRequestCode == null || normalizedStatementCode == null) {
            return;
        }
        if (!normalizedRequestCode.equals(normalizedStatementCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行对账单客户编码与收款单客户编码不一致");
        }
    }

    private String mergeCustomerCode(String currentCode, String nextCode) {
        String normalizedCurrentCode = trimToNull(currentCode);
        String normalizedNextCode = trimToNull(nextCode);
        if (normalizedCurrentCode == null) {
            return normalizedNextCode;
        }
        if (normalizedNextCode == null || normalizedCurrentCode.equals(normalizedNextCode)) {
            return normalizedCurrentCode;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一收款单不能核销不同客户编码的对账单");
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal normalizeAllocatedAmount(BigDecimal allocatedAmount, int lineNo) {
        BigDecimal normalized = TradeItemCalculator.safeBigDecimal(allocatedAmount);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行核销金额必须大于0");
        }
        return TradeItemCalculator.scaleAmount(normalized);
    }

    private void captureOriginalAllocationStatementIds(Receipt entity) {
        entity.setOriginalAllocationStatementIds(collectStatementIds(entity.getItems()));
    }

    private Set<Long> buildAffectedStatementIds(Receipt entity) {
        Set<Long> statementIds = new LinkedHashSet<>(entity.getOriginalAllocationStatementIds());
        statementIds.addAll(collectStatementIds(entity.getItems()));
        return statementIds;
    }

    private Set<Long> collectStatementIds(List<ReceiptAllocation> items) {
        Set<Long> statementIds = new LinkedHashSet<>();
        for (ReceiptAllocation item : items) {
            if (item.getSourceStatementId() != null) {
                statementIds.add(item.getSourceStatementId());
            }
        }
        return statementIds;
    }

    private Long resolveLegacySourceStatementId(List<ReceiptAllocation> items) {
        return items.size() == 1 ? items.get(0).getSourceStatementId() : null;
    }

    private void syncCustomerStatements(Set<Long> statementIds) {
        for (Long statementId : statementIds) {
            eventPublisher.publishEvent(new ReceiptSettledEvent(statementId));
        }
    }

    private ReceiptAllocationResponse toAllocationResponse(ReceiptAllocation item) {
        CustomerStatement statement = item.getSourceStatementId() == null
                ? null
                : customerStatementQueryService.findActiveById(item.getSourceStatementId()).orElse(null);
        return new ReceiptAllocationResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceStatementId(),
                statement == null ? null : statement.getStatementNo(),
                statement == null ? null : statement.getProjectName(),
                statement == null ? BigDecimal.ZERO : statement.getClosingAmount(),
                item.getAllocatedAmount()
        );
    }
}
