package com.leo.erp.statement.supplier.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatementItem;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementCandidateResponse;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import com.leo.erp.statement.service.StatementCandidateSupport;
import com.leo.erp.statement.service.StatementSourceCoverageValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

@Service
public class SupplierStatementSourceService {

    private static final String[] PURCHASE_INBOUND_CANDIDATE_SEARCH_FIELDS = {
            "inboundNo",
            "purchaseOrderNo",
            "supplierName",
            "warehouseName"
    };

    private final SupplierStatementRepository repository;
    private final PurchaseInboundRepository purchaseInboundRepository;
    private final PurchaseInboundItemQueryService purchaseInboundItemQueryService;
    private final SupplierRepository supplierRepository;

    public SupplierStatementSourceService(SupplierStatementRepository repository,
                                          PurchaseInboundRepository purchaseInboundRepository,
                                          PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                          SupplierRepository supplierRepository) {
        this.repository = repository;
        this.purchaseInboundRepository = purchaseInboundRepository;
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
        this.supplierRepository = supplierRepository;
    }

    Page<SupplierStatementCandidateResponse> candidatePage(PageQuery query, PageFilter filter) {
        Set<Long> occupiedInboundIds = toIdSet(
                repository.findOccupiedSourceInboundIdsExcludingCurrentStatement(filter.currentRecordId())
        );
        Specification<PurchaseInbound> spec = Specs.<PurchaseInbound>notDeleted()
                .and(Specs.keywordLike(filter.keyword(), PURCHASE_INBOUND_CANDIDATE_SEARCH_FIELDS))
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", StatusConstants.INBOUND_COMPLETED))
                .and(Specs.betweenIfPresent("inboundDate", filter.startDate(), filter.endDate()))
                .and(StatementCandidateSupport.excludeFieldValues("id", occupiedInboundIds));
        return purchaseInboundRepository.findAll(DataScopeContext.apply(spec), query.toPageable("id"))
                .map(this::toCandidateResponse);
    }

    SourceApplyResult applyItems(SupplierStatement entity,
                                 SupplierStatementRequest request,
                                 LongSupplier nextIdSupplier) {
        BigDecimal purchaseAmount = BigDecimal.ZERO;
        Map<Long, PurchaseInboundItem> sourceInboundItemMap = loadSourceInboundItemMap(request.items());
        validateSourceInbounds(request, sourceInboundItemMap, entity.getId());
        SupplierIdentity supplierIdentity = resolveSupplierIdentity(
                sourceInboundItemMap.values().stream()
                        .map(PurchaseInboundItem::getPurchaseInbound)
                        .distinct()
                        .toList(),
                request
        );
        entity.setSupplierId(supplierIdentity.id());
        entity.setSupplierCode(supplierIdentity.code());
        SettlementCompanySnapshot settlementCompany = resolveStatementSettlementCompany(sourceInboundItemMap.values().stream()
                .map(PurchaseInboundItem::getPurchaseInbound)
                .distinct()
                .toList());
        List<SupplierStatementItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                SupplierStatementItem::getId,
                SupplierStatementItemRequest::id,
                SupplierStatementItem::new,
                nextIdSupplier,
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
            item.setMaterialId(sourceInboundItem.getMaterialId());
            item.setWarehouseId(sourceInboundItem.getWarehouseId());
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
            BigDecimal weightAdjustmentAmount =
                    TradeItemCalculator.scaleAmount(sourceInboundItem.getWeightAdjustmentAmount());
            item.setWeightAdjustmentAmount(weightAdjustmentAmount);
            item.setUnitPrice(TradeItemCalculator.scaleAmount(sourceInboundItem.getUnitPrice()));
            BigDecimal amount = TradeItemCalculator.scaleAmount(sourceInboundItem.getAmount());
            item.setAmount(amount);
            purchaseAmount = purchaseAmount.add(amount).add(weightAdjustmentAmount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(SupplierStatementItem::getLineNo));
        return new SourceApplyResult(
                purchaseAmount,
                supplierIdentity.id(),
                supplierIdentity.code(),
                supplierIdentity.name(),
                settlementCompany.id(),
                settlementCompany.name()
        );
    }

    private Map<Long, PurchaseInboundItem> loadSourceInboundItemMap(List<SupplierStatementItemRequest> items) {
        Set<Long> uniqueSourceItemIds = new LinkedHashSet<>();
        for (SupplierStatementItemRequest item : items) {
            Long sourceItemId = item.sourceInboundItemId();
            if (sourceItemId != null && !uniqueSourceItemIds.add(sourceItemId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "来源采购入库明细ID重复");
            }
        }
        List<Long> sourceInboundItemIds = List.copyOf(uniqueSourceItemIds);
        if (sourceInboundItemIds.isEmpty()) {
            return Map.of();
        }
        return purchaseInboundItemQueryService.findAllActiveByIdIn(sourceInboundItemIds).stream()
                .collect(java.util.stream.Collectors.toMap(PurchaseInboundItem::getId, item -> item));
    }

    private void validateSourceInbounds(SupplierStatementRequest request,
                                        Map<Long, PurchaseInboundItem> sourceInboundItemMap,
                                        Long currentStatementId) {
        Map<Long, PurchaseInbound> requestedInbounds = new java.util.LinkedHashMap<>();
        for (PurchaseInboundItem item : sourceInboundItemMap.values()) {
            PurchaseInbound inbound = item.getPurchaseInbound();
            DataScopeContext.assertCanAccess(inbound);
            requestedInbounds.put(inbound.getId(), inbound);
            BusinessDocumentValidator.requireSameText(
                    request.supplierName(),
                    inbound.getSupplierName(),
                    "来源采购入库单存在不同供应商，不能合并生成供应商对账单"
            );
            String requestedSupplierCode = trimToNull(request.supplierCode());
            if (requestedSupplierCode != null) {
                BusinessDocumentValidator.requireSameText(
                        requestedSupplierCode,
                        inbound.getSupplierCode(),
                        "来源采购入库单供应商编码与对账单不一致"
                );
            }
            BusinessDocumentValidator.requireStatusIn(
                    inbound.getStatus(),
                    Set.of(StatusConstants.INBOUND_COMPLETED),
                    "来源采购入库单" + inbound.getInboundNo() + "未完成入库，不能生成供应商对账单"
            );
            if (request.settlementCompanyId() != null
                    && inbound.getSettlementCompanyId() != null
                    && !request.settlementCompanyId().equals(inbound.getSettlementCompanyId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购入库单存在不同采购结算主体，不能合并生成供应商对账单");
            }
            requireSameIdentity(request.supplierId(), inbound.getSupplierId(),
                    "来源采购入库单供应商ID与对账单不一致");
        }
        if (requestedInbounds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "供应商对账单来源采购入库单不能为空");
        }
        assertCompleteSourceItemCoverage(sourceInboundItemMap.values());
        assertSourceInboundsNotOccupied(requestedInbounds, currentStatementId);
    }

    private void assertCompleteSourceItemCoverage(Collection<PurchaseInboundItem> requestedItems) {
        requestedItems.stream()
                .map(PurchaseInboundItem::getPurchaseInbound)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(inbound -> StatementSourceCoverageValidator.requireAllEffectiveItems(
                        "来源采购入库单" + inbound.getInboundNo(),
                        inbound.getItems().stream().map(PurchaseInboundItem::getId).toList(),
                        requestedItems.stream()
                                .filter(item -> samePurchaseInbound(item.getPurchaseInbound(), inbound))
                                .map(PurchaseInboundItem::getId)
                                .toList()
                ));
    }

    private boolean samePurchaseInbound(PurchaseInbound left, PurchaseInbound right) {
        if (left == right) {
            return true;
        }
        return left != null
                && right != null
                && left.getId() != null
                && left.getId().equals(right.getId());
    }

    private void assertSourceInboundsNotOccupied(Map<Long, PurchaseInbound> requestedInbounds,
                                                 Long currentStatementId) {
        Set<Long> occupiedInboundIds = toIdSet(
                repository.findMatchingOccupiedSourceInboundIdsExcludingCurrentStatement(
                        requestedInbounds.keySet(),
                        currentStatementId
                )
        );
        for (PurchaseInbound inbound : requestedInbounds.values()) {
            if (occupiedInboundIds.contains(inbound.getId())) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "来源采购入库单" + inbound.getInboundNo() + "已生成供应商对账单"
                );
            }
        }
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
            requireSameIdentity(source.materialId(), sourceInboundItem.getMaterialId(),
                    "第" + lineNo + "行商品ID与来源采购入库明细不一致");
            requireSameIdentity(source.warehouseId(), sourceInboundItem.getWarehouseId(),
                    "第" + lineNo + "行仓库ID与来源采购入库明细不一致");
            return sourceInboundItem;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购入库明细不能为空");
    }

    private String resolveSupplierCode(String requestSupplierCode, String supplierName) {
        String explicitCode = trimToNull(requestSupplierCode);
        if (explicitCode != null || supplierRepository == null) {
            return explicitCode;
        }
        String normalizedSupplierName = trimToNull(supplierName);
        if (normalizedSupplierName == null) {
            return null;
        }
        return supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(normalizedSupplierName)
                .map(com.leo.erp.master.supplier.domain.entity.Supplier::getSupplierCode)
                .orElse(null);
    }

    private SupplierIdentity resolveSupplierIdentity(List<PurchaseInbound> inbounds,
                                                     SupplierStatementRequest request) {
        List<Long> supplierIds = inbounds.stream()
                .map(PurchaseInbound::getSupplierId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (supplierIds.size() > 1) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "来源采购入库单存在不同供应商ID，不能合并生成供应商对账单"
            );
        }
        Long supplierId = supplierIds.isEmpty() ? null : supplierIds.get(0);
        requireSameIdentity(request.supplierId(), supplierId, "供应商ID与来源采购入库单不一致");
        List<String> supplierCodes = inbounds.stream()
                .map(PurchaseInbound::getSupplierCode)
                .map(this::trimToNull)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (supplierCodes.size() > 1) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "来源采购入库单存在不同供应商编码，不能合并生成供应商对账单"
            );
        }
        String supplierCode = supplierCodes.isEmpty()
                ? resolveSupplierCode(request.supplierCode(), request.supplierName())
                : supplierCodes.get(0);
        if (supplierCode == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商编码不能为空");
        }
        List<String> supplierNames = inbounds.stream()
                .map(PurchaseInbound::getSupplierName)
                .map(this::trimToNull)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        String supplierName = supplierNames.isEmpty() ? trimToNull(request.supplierName()) : supplierNames.get(0);
        return new SupplierIdentity(supplierId, supplierCode, supplierName);
    }

    private void requireSameIdentity(Long requestedId, Long sourceId, String message) {
        if (requestedId != null && !requestedId.equals(sourceId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    private String trimToNull(String value) {
        return BusinessDocumentValidator.trimToNull(value);
    }

    private Set<Long> toIdSet(Collection<Long> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }

    private SettlementCompanySnapshot resolveStatementSettlementCompany(List<PurchaseInbound> inbounds) {
        List<SettlementCompanySnapshot> snapshots = inbounds.stream()
                .map(inbound -> new SettlementCompanySnapshot(inbound.getSettlementCompanyId(), trimToNull(inbound.getSettlementCompanyName())))
                .filter(snapshot -> snapshot.id() != null || snapshot.name() != null)
                .distinct()
                .toList();
        if (snapshots.isEmpty()) {
            return new SettlementCompanySnapshot(null, null);
        }
        if (snapshots.size() > 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购入库单存在不同采购结算主体，不能合并生成供应商对账单");
        }
        return snapshots.get(0);
    }

    private SupplierStatementCandidateResponse toCandidateResponse(PurchaseInbound inbound) {
        return new SupplierStatementCandidateResponse(
                inbound.getId(),
                inbound.getInboundNo(),
                inbound.getSupplierName(),
                inbound.getSettlementCompanyId(),
                inbound.getSettlementCompanyName(),
                inbound.getWarehouseName(),
                inbound.getInboundDate(),
                inbound.getSettlementMode(),
                inbound.getTotalWeight(),
                inbound.getTotalAmount(),
                inbound.getStatus(),
                inbound.getSupplierId(),
                inbound.getWarehouseId()
        );
    }

    record SourceApplyResult(
            BigDecimal purchaseAmount,
            Long supplierId,
            String supplierCode,
            String supplierName,
            Long settlementCompanyId,
            String settlementCompanyName
    ) {
        SourceApplyResult(BigDecimal purchaseAmount,
                          String supplierCode,
                          String supplierName,
                          Long settlementCompanyId,
                          String settlementCompanyName) {
            this(purchaseAmount, null, supplierCode, supplierName, settlementCompanyId, settlementCompanyName);
        }

        SourceApplyResult(BigDecimal purchaseAmount,
                          Long settlementCompanyId,
                          String settlementCompanyName) {
            this(purchaseAmount, null, null, null, settlementCompanyId, settlementCompanyName);
        }
    }

    private record SupplierIdentity(Long id, String code, String name) {
    }

    private record SettlementCompanySnapshot(Long id, String name) {
    }
}
