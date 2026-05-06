package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
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
import java.time.LocalDate;
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

    public Page<ReceiptResponse> page(PageQuery query,
                                      String keyword,
                                      String customerName,
                                      String status,
                                      LocalDate startDate,
                                      LocalDate endDate) {
        Specification<Receipt> spec = Specs.<Receipt>notDeleted()
                .and(Specs.keywordLike(keyword, "receiptNo", "customerName", "projectName"))
                .and(Specs.equalIfPresent("customerName", customerName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("receiptDate", startDate, endDate));
        return page(query, spec, receiptRepository);
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
    protected String notFoundMessage() {
        return "收款单不存在";
    }

    @Override
    protected ReceiptResponse toDetailResponse(Receipt entity) {
        ReceiptResponse response = receiptMapper.toResponse(entity);
        return new ReceiptResponse(
                response.id(),
                response.receiptNo(),
                response.customerName(),
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
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "receipts",
                entity.getStatus(),
                request.status(),
                RECEIPT_STATUS_SETTLED
        );
        captureOriginalAllocationStatementIds(entity);
        entity.setReceiptNo(request.receiptNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setReceiptDate(request.receiptDate());
        entity.setPayType(request.payType());
        entity.setAmount(TradeItemCalculator.scaleAmount(request.amount()));
        entity.setStatus(request.status());
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());
        applyAllocations(entity, request);
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

    private void applyAllocations(Receipt entity, ReceiptRequest request) {
        List<ReceiptAllocationRequest> allocationRequests = normalizeAllocationRequests(request);
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
            validateLinkedCustomerStatement(request, entity.getId(), statement, allocatedAmount, requestAllocatedAmountMap, i + 1);

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
        entity.getItems().sort(java.util.Comparator.comparing(ReceiptAllocation::getLineNo));
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

    private CustomerStatement requireAccessibleCustomerStatement(Long statementId) {
        CustomerStatement statement = customerStatementQueryService.requireActiveById(statementId);
        resourceRecordAccessGuard.assertCurrentUserCanAccess(
                "customer-statements",
                ResourcePermissionCatalog.READ,
                statement
        );
        return statement;
    }

    private void validateLinkedCustomerStatement(ReceiptRequest request,
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
        if (requestAllocatedAmountMap.containsKey(statement.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一收款单不能重复核销同一客户对账单");
        }
        if (RECEIPT_STATUS_SETTLED.equals(request.status())) {
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
