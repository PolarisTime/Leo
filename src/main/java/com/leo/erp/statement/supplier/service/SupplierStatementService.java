package com.leo.erp.statement.supplier.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatementItem;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import com.leo.erp.statement.supplier.mapper.SupplierStatementMapper;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemResponse;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
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
public class SupplierStatementService extends AbstractCrudService<SupplierStatement, SupplierStatementRequest, SupplierStatementResponse> {

    private final SupplierStatementRepository repository;
    private final SupplierStatementMapper supplierStatementMapper;
    private final StatementSettlementSyncService statementSettlementSyncService;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public SupplierStatementService(SupplierStatementRepository repository,
                                    SnowflakeIdGenerator idGenerator,
                                    SupplierStatementMapper supplierStatementMapper,
                                    StatementSettlementSyncService statementSettlementSyncService,
                                    WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.supplierStatementMapper = supplierStatementMapper;
        this.statementSettlementSyncService = statementSettlementSyncService;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<SupplierStatementResponse> page(
            PageQuery query,
            String keyword,
            String supplierName,
            String status,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        Specification<SupplierStatement> spec = Specs.<SupplierStatement>notDeleted()
                .and(Specs.keywordLike(keyword, "statementNo", "supplierName", "sourceInboundNos"))
                .and(Specs.equalIfPresent("supplierName", supplierName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("endDate", periodStart, periodEnd));
        return page(query, spec, repository);
    }

    @Override
    protected SupplierStatementResponse toDetailResponse(SupplierStatement entity) {
        SupplierStatementResponse response = supplierStatementMapper.toResponse(entity);
        return new SupplierStatementResponse(
                response.id(),
                response.statementNo(),
                response.sourceInboundNos(),
                response.supplierName(),
                response.startDate(),
                response.endDate(),
                response.purchaseAmount(),
                response.paymentAmount(),
                response.closingAmount(),
                response.status(),
                response.remark(),
                entity.getItems().stream().map(this::toItemResponse).toList()
        );
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
    protected String notFoundMessage() {
        return "供应商对账单不存在";
    }

    @Override
    protected void apply(SupplierStatement entity, SupplierStatementRequest request) {
        String nextStatus = (request.status() == null || request.status().isBlank()) ? "待确认" : request.status();
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "supplier-statements",
                entity.getStatus(),
                nextStatus,
                "已确认"
        );
        entity.setStatementNo(request.statementNo());
        entity.setSupplierName(request.supplierName());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        BigDecimal purchaseAmount = BigDecimal.ZERO;
        LinkedHashSet<String> sourceInboundNos = new LinkedHashSet<>();
        List<SupplierStatementItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                SupplierStatementItem::getId,
                SupplierStatementItemRequest::id,
                SupplierStatementItem::new,
                this::nextId,
                SupplierStatementItem::setId
        );
        for (int i = 0; i < request.items().size(); i++) {
            SupplierStatementItemRequest source = request.items().get(i);
            SupplierStatementItem item = items.get(i);
            item.setSupplierStatement(entity);
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
            BigDecimal theoreticalWeightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon());
            BigDecimal weightTon = source.weightTon() == null
                    ? theoreticalWeightTon
                    : TradeItemCalculator.scaleWeightTon(source.weightTon());
            item.setWeightTon(weightTon);
            item.setWeighWeightTon(source.weighWeightTon() == null ? null : TradeItemCalculator.scaleWeightTon(source.weighWeightTon()));
            BigDecimal weightAdjustmentTon = source.weightAdjustmentTon() == null
                    ? TradeItemCalculator.scaleWeightTon(weightTon.subtract(theoreticalWeightTon))
                    : TradeItemCalculator.scaleWeightTon(source.weightAdjustmentTon());
            item.setWeightAdjustmentTon(weightAdjustmentTon);
            BigDecimal weightAdjustmentAmount = source.weightAdjustmentAmount() == null
                    ? TradeItemCalculator.calculateAmount(weightAdjustmentTon, source.unitPrice())
                    : TradeItemCalculator.scaleAmount(source.weightAdjustmentAmount());
            item.setWeightAdjustmentAmount(weightAdjustmentAmount);
            item.setUnitPrice(source.unitPrice());
            BigDecimal amount = TradeItemCalculator.calculateAmount(item.getWeightTon(), source.unitPrice());
            item.setAmount(amount);
            sourceInboundNos.add(source.sourceNo());
            purchaseAmount = purchaseAmount.add(amount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(SupplierStatementItem::getLineNo));
        entity.setSourceInboundNos(String.join(", ", sourceInboundNos));
        BigDecimal paymentAmount = request.paymentAmount() == null
                ? BigDecimal.ZERO
                : TradeItemCalculator.scaleAmount(request.paymentAmount());
        if (paymentAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商对账单付款金额不能为负数");
        }
        if (paymentAmount.compareTo(purchaseAmount) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商对账单采购金额不能低于已付款金额");
        }
        entity.setPurchaseAmount(purchaseAmount);
        entity.setPaymentAmount(paymentAmount);
        entity.setClosingAmount(TradeItemCalculator.scaleAmount(purchaseAmount.subtract(paymentAmount).max(BigDecimal.ZERO)));
    }

    @Override
    protected SupplierStatement saveEntity(SupplierStatement entity) {
        return repository.save(entity);
    }

    @Override
    protected SupplierStatementResponse toResponse(SupplierStatement entity) {
        return supplierStatementMapper.toResponse(entity);
    }

    private SupplierStatementItemResponse toItemResponse(SupplierStatementItem item) {
        return new SupplierStatementItemResponse(
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
                item.getWeighWeightTon(),
                item.getWeightAdjustmentTon(),
                item.getWeightAdjustmentAmount(),
                item.getUnitPrice(),
                item.getAmount()
        );
    }
}
