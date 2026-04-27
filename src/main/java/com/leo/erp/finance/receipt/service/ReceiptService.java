package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.repository.ReceiptRepository;
import com.leo.erp.finance.receipt.mapper.ReceiptMapper;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.service.CustomerStatementQueryService;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class ReceiptService extends AbstractCrudService<Receipt, ReceiptRequest, ReceiptResponse> {

    private final ReceiptRepository receiptRepository;
    private final ReceiptMapper receiptMapper;
    private final CustomerStatementQueryService customerStatementQueryService;
    private final StatementSettlementSyncService statementSettlementSyncService;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public ReceiptService(ReceiptRepository receiptRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          ReceiptMapper receiptMapper,
                          CustomerStatementQueryService customerStatementQueryService,
                          StatementSettlementSyncService statementSettlementSyncService,
                          ResourceRecordAccessGuard resourceRecordAccessGuard,
                          WorkflowTransitionGuard workflowTransitionGuard) {
        super(snowflakeIdGenerator);
        this.receiptRepository = receiptRepository;
        this.receiptMapper = receiptMapper;
        this.customerStatementQueryService = customerStatementQueryService;
        this.statementSettlementSyncService = statementSettlementSyncService;
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
    protected void apply(Receipt entity, ReceiptRequest request) {
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "receipts",
                entity.getStatus(),
                request.status(),
                StatementSettlementSyncService.RECEIPT_STATUS_SETTLED
        );
        entity.setReceiptNo(request.receiptNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        CustomerStatement linkedStatement = resolveLinkedCustomerStatement(request, entity.getId());
        entity.setSourceStatementId(linkedStatement.getId());
        entity.setReceiptDate(request.receiptDate());
        entity.setPayType(request.payType());
        entity.setAmount(request.amount());
        entity.setStatus(request.status());
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());
    }

    @Override
    protected Receipt saveEntity(Receipt entity) {
        Long originalSourceStatementId = entity.getOriginalSourceStatementId();
        Receipt saved = receiptRepository.save(entity);
        syncCustomerStatements(originalSourceStatementId, saved.getSourceStatementId());
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

    private CustomerStatement resolveLinkedCustomerStatement(ReceiptRequest request, Long currentReceiptId) {
        if (request.sourceStatementId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "收款单必须关联客户对账单");
        }
        CustomerStatement statement = customerStatementQueryService.requireActiveById(request.sourceStatementId());
        resourceRecordAccessGuard.assertCurrentUserCanAccess(
                "customer-statements",
                ResourcePermissionCatalog.READ,
                statement
        );
        if (!statement.getCustomerName().equals(request.customerName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "收款单客户与客户对账单客户不一致");
        }
        if (!statement.getProjectName().equals(request.projectName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "收款单项目与客户对账单项目不一致");
        }
        if (StatementSettlementSyncService.RECEIPT_STATUS_SETTLED.equals(request.status())) {
            BigDecimal settledAmount = safeAmount(receiptRepository.sumAmountBySourceStatementIdAndStatusExcludingId(
                    statement.getId(),
                    StatementSettlementSyncService.RECEIPT_STATUS_SETTLED,
                    currentReceiptId
            ));
            BigDecimal nextSettledAmount = settledAmount.add(request.amount());
            if (nextSettledAmount.compareTo(statement.getSalesAmount()) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "关联客户对账单累计收款金额不能超过销售金额");
            }
        }
        return statement;
    }

    private void syncCustomerStatements(Long originalSourceStatementId, Long currentSourceStatementId) {
        Set<Long> statementIds = new LinkedHashSet<>();
        if (originalSourceStatementId != null) {
            statementIds.add(originalSourceStatementId);
        }
        if (currentSourceStatementId != null) {
            statementIds.add(currentSourceStatementId);
        }
        for (Long statementId : statementIds) {
            customerStatementQueryService.findActiveById(statementId)
                    .ifPresent(statementSettlementSyncService::syncCustomerStatement);
        }
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
