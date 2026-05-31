package com.leo.erp.statement.supplier.service;

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
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatementItem;
import com.leo.erp.statement.supplier.mapper.SupplierStatementMapper;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementCandidateResponse;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemResponse;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
import com.leo.erp.statement.service.StatementCandidateSupport;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class SupplierStatementService extends AbstractCrudService<SupplierStatement, SupplierStatementRequest, SupplierStatementResponse> {

    private static final String[] PURCHASE_INBOUND_CANDIDATE_SEARCH_FIELDS = {
            "inboundNo",
            "purchaseOrderNo",
            "supplierName",
            "warehouseName"
    };

    private final SupplierStatementRepository repository;
    private final SupplierStatementMapper supplierStatementMapper;
    private final PurchaseInboundRepository purchaseInboundRepository;
    private final PurchaseInboundItemQueryService purchaseInboundItemQueryService;
    private final StatementSettlementSyncService statementSettlementSyncService;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public SupplierStatementService(SupplierStatementRepository repository,
                                    SnowflakeIdGenerator idGenerator,
                                    SupplierStatementMapper supplierStatementMapper,
                                    PurchaseInboundRepository purchaseInboundRepository,
                                    PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                    StatementSettlementSyncService statementSettlementSyncService,
                                    WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.supplierStatementMapper = supplierStatementMapper;
        this.purchaseInboundRepository = purchaseInboundRepository;
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
        this.statementSettlementSyncService = statementSettlementSyncService;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<SupplierStatementResponse> page(PageQuery query, PageFilter filter) {
        Specification<SupplierStatement> spec = Specs.<SupplierStatement>keywordLike(filter.keyword(), "statementNo", "supplierName")
                .and(Specs.equalIfPresent("supplierName", filter.name()))
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
        Set<String> occupiedInboundNos = collectOccupiedInboundNos(null);
        Specification<PurchaseInbound> spec = Specs.<PurchaseInbound>notDeleted()
                .and(Specs.keywordLike(filter.keyword(), PURCHASE_INBOUND_CANDIDATE_SEARCH_FIELDS))
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalIfPresent("status", StatusConstants.PURCHASE_COMPLETED))
                .and(Specs.betweenIfPresent("inboundDate", filter.startDate(), filter.endDate()))
                .and(StatementCandidateSupport.excludeFieldValues("inboundNo", occupiedInboundNos));
        return purchaseInboundRepository.findAll(DataScopeContext.apply(spec), query.toPageable("id"))
                .map(this::toCandidateResponse);
    }

    @Override
    protected SupplierStatementResponse toDetailResponse(SupplierStatement entity) {
        SupplierStatementResponse response = supplierStatementMapper.toResponse(entity);
        return new SupplierStatementResponse(
                response.id(),
                response.statementNo(),
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
    protected SupplierStatementRequest normalizeCreateRequest(SupplierStatementRequest request, long entityId) {
        return new SupplierStatementRequest(
                resolveCreateBusinessNo("supplier-statement", request.statementNo(), entityId),
                request.supplierName(),
                request.startDate(),
                request.endDate(),
                request.purchaseAmount(),
                request.paymentAmount(),
                request.closingAmount(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected SupplierStatementRequest normalizeUpdateRequest(SupplierStatement entity, SupplierStatementRequest request) {
        return new SupplierStatementRequest(
                entity.getStatementNo(),
                request.supplierName(),
                request.startDate(),
                request.endDate(),
                request.purchaseAmount(),
                request.paymentAmount(),
                request.closingAmount(),
                request.status(),
                request.remark(),
                request.items()
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
    protected void apply(SupplierStatement entity, SupplierStatementRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.PENDING_CONFIRM,
                "供应商对账单状态",
                StatusConstants.ALLOWED_STATEMENT_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "supplier-statement",
                entity.getStatus(),
                nextStatus,
                StatusConstants.CONFIRMED
        );
        entity.setStatementNo(request.statementNo());
        entity.setSupplierName(request.supplierName());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        BigDecimal purchaseAmount = BigDecimal.ZERO;
        Map<Long, PurchaseInboundItem> sourceInboundItemMap = loadSourceInboundItemMap(request.items());
        validateSourceInbounds(request, sourceInboundItemMap, entity.getId());
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
            PurchaseInboundItem sourceInboundItem = resolveSourceInboundItem(source, sourceInboundItemMap, i + 1);
            SupplierStatementItem item = items.get(i);
            item.setSupplierStatement(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(sourceInboundItem.getPurchaseInbound().getInboundNo());
            item.setSourceInboundItemId(sourceInboundItem.getId());
            item.setMaterialCode(sourceInboundItem.getMaterialCode());
            item.setBrand(sourceInboundItem.getBrand());
            item.setCategory(sourceInboundItem.getCategory());
            item.setMaterial(sourceInboundItem.getMaterial());
            item.setSpec(sourceInboundItem.getSpec());
            item.setLength(sourceInboundItem.getLength());
            item.setUnit(sourceInboundItem.getUnit());
            item.setBatchNo(sourceInboundItem.getBatchNo());
            item.setQuantity(sourceInboundItem.getQuantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(sourceInboundItem.getQuantityUnit()));
            item.setPieceWeightTon(TradeItemCalculator.scaleWeightTon(sourceInboundItem.getPieceWeightTon()));
            item.setPiecesPerBundle(sourceInboundItem.getPiecesPerBundle());
            item.setWeightTon(TradeItemCalculator.scaleWeightTon(sourceInboundItem.getWeightTon()));
            item.setWeighWeightTon(sourceInboundItem.getWeighWeightTon() == null
                    ? null
                    : TradeItemCalculator.scaleWeightTon(sourceInboundItem.getWeighWeightTon()));
            item.setWeightAdjustmentTon(TradeItemCalculator.scaleWeightTon(sourceInboundItem.getWeightAdjustmentTon()));
            item.setWeightAdjustmentAmount(TradeItemCalculator.scaleAmount(sourceInboundItem.getWeightAdjustmentAmount()));
            item.setUnitPrice(TradeItemCalculator.scaleAmount(sourceInboundItem.getUnitPrice()));
            BigDecimal amount = TradeItemCalculator.scaleAmount(sourceInboundItem.getAmount());
            item.setAmount(amount);
            purchaseAmount = purchaseAmount.add(amount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(SupplierStatementItem::getLineNo));
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
                item.getSourceInboundItemId(),
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

    private Map<Long, PurchaseInboundItem> loadSourceInboundItemMap(List<SupplierStatementItemRequest> items) {
        List<Long> sourceInboundItemIds = items.stream()
                .map(SupplierStatementItemRequest::sourceInboundItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (sourceInboundItemIds.isEmpty()) {
            return Map.of();
        }
        return purchaseInboundItemQueryService.findAllActiveByIdIn(sourceInboundItemIds).stream()
                .collect(java.util.stream.Collectors.toMap(PurchaseInboundItem::getId, item -> item));
    }

    private void validateSourceInbounds(SupplierStatementRequest request,
                                        Map<Long, PurchaseInboundItem> sourceInboundItemMap,
                                        Long currentStatementId) {
        Set<String> requestedInboundNos = new LinkedHashSet<>();
        for (PurchaseInboundItem item : sourceInboundItemMap.values()) {
            PurchaseInbound inbound = item.getPurchaseInbound();
            DataScopeContext.assertCanAccess(inbound);
            requestedInboundNos.add(inbound.getInboundNo());
            if (!request.supplierName().trim().equals(inbound.getSupplierName())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购入库单存在不同供应商，不能合并生成供应商对账单");
            }
            if (!StatusConstants.PURCHASE_COMPLETED.equals(inbound.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购入库单" + inbound.getInboundNo() + "未完成采购，不能生成供应商对账单");
            }
        }
        if (requestedInboundNos.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "供应商对账单来源采购入库单不能为空");
        }
        assertSourceInboundsNotOccupied(requestedInboundNos, currentStatementId);
    }

    private void assertSourceInboundsNotOccupied(Set<String> requestedInboundNos, Long currentStatementId) {
        List<SupplierStatement> occupiedStatements =
                repository.findAllBySourceNosExcludingCurrentStatement(requestedInboundNos, currentStatementId);
        Set<String> occupiedInboundNos = occupiedStatements.stream()
                .flatMap(entity -> entity.getItems().stream())
                .map(SupplierStatementItem::getSourceNo)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String inboundNo : requestedInboundNos) {
            if (occupiedInboundNos.contains(inboundNo)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购入库单" + inboundNo + "已生成供应商对账单");
            }
        }
    }

    private Set<String> collectOccupiedInboundNos(Long currentStatementId) {
        Specification<SupplierStatement> spec = Specs.notDeleted();
        if (currentStatementId != null) {
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("id"), currentStatementId));
        }
        Set<String> occupiedInboundNos = new LinkedHashSet<>();
        repository.findAll(spec).stream()
                .flatMap(entity -> entity.getItems().stream())
                .map(SupplierStatementItem::getSourceNo)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .forEach(occupiedInboundNos::add);
        return occupiedInboundNos;
    }

    private PurchaseInboundItem resolveSourceInboundItem(SupplierStatementItemRequest source,
                                                         Map<Long, PurchaseInboundItem> sourceInboundItemMap,
                                                         int lineNo) {
        Long sourceInboundItemId = source.sourceInboundItemId();
        if (sourceInboundItemId != null) {
            PurchaseInboundItem sourceInboundItem = sourceInboundItemMap.get(sourceInboundItemId);
            if (sourceInboundItem == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购入库明细不存在");
            }
            return sourceInboundItem;
        }
        String sourceNo = source.sourceNo() == null ? "" : source.sourceNo().trim();
        if (sourceNo.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购入库明细不能为空");
        }
        return purchaseInboundRepository.findAllByDeletedFlagFalse().stream()
                .filter(inbound -> sourceNo.equals(inbound.getInboundNo()))
                .flatMap(inbound -> inbound.getItems().stream())
                .filter(item -> matchesLegacySupplierItem(source, item))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购入库明细不存在"));
    }

    private boolean matchesLegacySupplierItem(SupplierStatementItemRequest source, PurchaseInboundItem item) {
        return item.getMaterialCode().equals(source.materialCode())
                && item.getBrand().equals(source.brand())
                && item.getCategory().equals(source.category())
                && item.getMaterial().equals(source.material())
                && item.getSpec().equals(source.spec())
                && java.util.Objects.equals(item.getLength(), source.length())
                && item.getQuantity().equals(source.quantity())
                && TradeItemCalculator.normalizeQuantityUnit(item.getQuantityUnit())
                .equals(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
    }

    private SupplierStatementCandidateResponse toCandidateResponse(PurchaseInbound inbound) {
        return new SupplierStatementCandidateResponse(
                inbound.getId(),
                inbound.getInboundNo(),
                inbound.getSupplierName(),
                inbound.getWarehouseName(),
                inbound.getInboundDate(),
                inbound.getSettlementMode(),
                inbound.getTotalWeight(),
                inbound.getTotalAmount(),
                inbound.getStatus()
        );
    }
}
