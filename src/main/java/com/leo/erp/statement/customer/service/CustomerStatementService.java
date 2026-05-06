package com.leo.erp.statement.customer.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import com.leo.erp.statement.customer.mapper.CustomerStatementMapper;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerStatementService extends AbstractCrudService<CustomerStatement, CustomerStatementRequest, CustomerStatementResponse> {

    private final CustomerStatementRepository repository;
    private final CustomerStatementMapper customerStatementMapper;
    private final StatementSettlementSyncService statementSettlementSyncService;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public CustomerStatementService(CustomerStatementRepository repository,
                                    SnowflakeIdGenerator idGenerator,
                                    CustomerStatementMapper customerStatementMapper,
                                    StatementSettlementSyncService statementSettlementSyncService,
                                    WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.customerStatementMapper = customerStatementMapper;
        this.statementSettlementSyncService = statementSettlementSyncService;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<CustomerStatementResponse> page(
            PageQuery query,
            String keyword,
            String customerName,
            String status,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        Specification<CustomerStatement> spec = Specs.<CustomerStatement>notDeleted()
                .and(Specs.keywordLike(keyword, "statementNo", "customerName", "projectName", "sourceOrderNos"))
                .and(Specs.equalIfPresent("customerName", customerName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("endDate", periodStart, periodEnd));
        return page(query, spec, repository);
    }

    @Override
    protected CustomerStatementResponse toDetailResponse(CustomerStatement entity) {
        CustomerStatementResponse response = customerStatementMapper.toResponse(entity);
        return new CustomerStatementResponse(
                response.id(),
                response.statementNo(),
                response.sourceOrderNos(),
                response.customerName(),
                response.projectName(),
                response.startDate(),
                response.endDate(),
                response.salesAmount(),
                response.receiptAmount(),
                response.closingAmount(),
                response.status(),
                response.remark(),
                entity.getItems().stream().map(this::toItemResponse).toList()
        );
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
    protected String notFoundMessage() {
        return "客户对账单不存在";
    }

    @Override
    protected void apply(CustomerStatement entity, CustomerStatementRequest request) {
        String nextStatus = (request.status() == null || request.status().isBlank()) ? "待确认" : request.status();
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "customer-statements",
                entity.getStatus(),
                nextStatus,
                "已确认"
        );
        entity.setStatementNo(request.statementNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        BigDecimal salesAmount = BigDecimal.ZERO;
        LinkedHashSet<String> sourceOrderNos = new LinkedHashSet<>();
        List<CustomerStatementItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                CustomerStatementItem::getId,
                CustomerStatementItemRequest::id,
                CustomerStatementItem::new,
                this::nextId,
                CustomerStatementItem::setId
        );
        for (int i = 0; i < request.items().size(); i++) {
            CustomerStatementItemRequest source = request.items().get(i);
            CustomerStatementItem item = items.get(i);
            item.setCustomerStatement(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(source.sourceNo());
            item.setMaterialCode(source.materialCode());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setUnit(source.unit());
            item.setBatchNo(source.batchNo());
            item.setQuantity(source.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
            item.setPieceWeightTon(source.pieceWeightTon());
            item.setPiecesPerBundle(source.piecesPerBundle());
            item.setWeightTon(TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon()));
            item.setUnitPrice(source.unitPrice());
            BigDecimal amount = TradeItemCalculator.calculateAmount(item.getWeightTon(), source.unitPrice());
            item.setAmount(amount);
            sourceOrderNos.add(source.sourceNo());
            salesAmount = salesAmount.add(amount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(CustomerStatementItem::getLineNo));
        entity.setSourceOrderNos(String.join(", ", sourceOrderNos));
        BigDecimal receiptAmount = request.receiptAmount() == null
                ? BigDecimal.ZERO
                : TradeItemCalculator.scaleAmount(request.receiptAmount());
        if (receiptAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户对账单收款金额不能为负数");
        }
        if (receiptAmount.compareTo(salesAmount) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户对账单销售金额不能低于已收款金额");
        }
        entity.setSalesAmount(salesAmount);
        entity.setReceiptAmount(receiptAmount);
        entity.setClosingAmount(TradeItemCalculator.scaleAmount(salesAmount.subtract(receiptAmount).max(BigDecimal.ZERO)));
    }

    @Override
    protected CustomerStatement saveEntity(CustomerStatement entity) {
        return repository.save(entity);
    }

    @Override
    protected CustomerStatementResponse toResponse(CustomerStatement entity) {
        return customerStatementMapper.toResponse(entity);
    }

    private CustomerStatementItemResponse toItemResponse(CustomerStatementItem item) {
        return new CustomerStatementItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceNo(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getBatchNo(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                item.getUnitPrice(),
                item.getAmount()
        );
    }
}
