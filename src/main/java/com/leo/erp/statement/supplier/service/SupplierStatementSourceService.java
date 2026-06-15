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
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    BigDecimal applyItems(SupplierStatement entity,
                          SupplierStatementRequest request,
                          LongSupplier nextIdSupplier) {
        BigDecimal purchaseAmount = BigDecimal.ZERO;
        entity.setSupplierCode(resolveSupplierCode(request.supplierCode(), request.supplierName()));
        Map<Long, PurchaseInboundItem> sourceInboundItemMap = loadSourceInboundItemMap(request.items());
        validateSourceInbounds(request, sourceInboundItemMap, entity.getId());
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
        return purchaseAmount;
    }

    Set<String> collectOccupiedInboundNos(Long currentStatementId) {
        org.springframework.data.jpa.domain.Specification<SupplierStatement> spec =
                com.leo.erp.common.persistence.Specs.notDeleted();
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
            BusinessDocumentValidator.requireSameText(
                    request.supplierName(),
                    inbound.getSupplierName(),
                    "来源采购入库单存在不同供应商，不能合并生成供应商对账单"
            );
            BusinessDocumentValidator.requireStatusIn(
                    inbound.getStatus(),
                    Set.of(StatusConstants.PURCHASE_COMPLETED),
                    "来源采购入库单" + inbound.getInboundNo() + "未完成采购，不能生成供应商对账单"
            );
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

    private String trimToNull(String value) {
        return BusinessDocumentValidator.trimToNull(value);
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
