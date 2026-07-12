package com.leo.erp.contract.purchase.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.contract.purchase.domain.entity.PurchaseContract;
import com.leo.erp.contract.purchase.domain.entity.PurchaseContractItem;
import com.leo.erp.contract.purchase.repository.PurchaseContractRepository;
import com.leo.erp.contract.purchase.mapper.PurchaseContractMapper;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractRequest;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractResponse;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractItemRequest;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractItemResponse;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class PurchaseContractService extends AbstractCrudService<PurchaseContract, PurchaseContractRequest, PurchaseContractResponse> {

    private static final Logger log = LoggerFactory.getLogger(PurchaseContractService.class);

    private final PurchaseContractRepository repository;
    private final PurchaseContractMapper purchaseContractMapper;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final SupplierRepository supplierRepository;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;

    public PurchaseContractService(PurchaseContractRepository repository,
                                   SnowflakeIdGenerator idGenerator,
                                   PurchaseContractMapper purchaseContractMapper,
                                   WorkflowTransitionGuard workflowTransitionGuard) {
        this(repository, idGenerator, purchaseContractMapper, workflowTransitionGuard, null, null);
    }

    @Autowired
    public PurchaseContractService(PurchaseContractRepository repository,
                                   SnowflakeIdGenerator idGenerator,
                                   PurchaseContractMapper purchaseContractMapper,
                                   WorkflowTransitionGuard workflowTransitionGuard,
                                   SupplierRepository supplierRepository,
                                   TradeItemMaterialSupport tradeItemMaterialSupport) {
        super(idGenerator);
        this.repository = repository;
        this.purchaseContractMapper = purchaseContractMapper;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.supplierRepository = supplierRepository;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
    }

    public Page<PurchaseContractResponse> page(PageQuery query, PageFilter filter) {
        Specification<PurchaseContract> spec = Specs.<PurchaseContract>keywordLike(filter.keyword(), "contractNo", "supplierName", "buyerName")
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("signDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    private static final String[] PURCHASE_CONTRACT_SEARCH_FIELDS = {
            "contractNo",
            "supplierName",
            "buyerName"
    };

    public java.util.List<PurchaseContractResponse> search(String keyword, int maxSize) {
        return search(keyword, PURCHASE_CONTRACT_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected PurchaseContractResponse toDetailResponse(PurchaseContract entity) {
        PurchaseContractResponse response = purchaseContractMapper.toResponse(entity);
        return new PurchaseContractResponse(
                response.id(), response.contractNo(), entity.getSupplierId(), entity.getSupplierCode(),
                response.supplierName(),
                response.signDate(), response.effectiveDate(), response.expireDate(),
                response.buyerName(), response.totalWeight(), response.totalAmount(),
                response.status(), response.deletedFlag(), response.remark(),
                entity.getItems().stream().map(item -> new PurchaseContractItemResponse(
                        item.getId(), item.getLineNo(), item.getMaterialId(), item.getMaterialCode(),
                        item.getBrand(), item.getCategory(), item.getMaterial(),
                        item.getSpec(), item.getLength(), item.getUnit(),
                        item.getQuantity(), item.getQuantityUnit(), item.getPieceWeightTon(),
                        item.getPiecesPerBundle(), item.getWeightTon(),
                        item.getUnitPrice(), item.getAmount()
                )).toList()
        );
    }

    @Override
    protected void validateCreate(PurchaseContractRequest request) {
        if (repository.existsByContractNoAndDeletedFlagFalse(request.contractNo())) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "采购合同号已存在");
        }
    }

    @Override
    protected void validateUpdate(PurchaseContract entity, PurchaseContractRequest request) {
        if (!entity.getContractNo().equals(request.contractNo())
                && repository.existsByContractNoAndDeletedFlagFalse(request.contractNo())) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "采购合同号已存在");
        }
        assertLineItemsUnchanged(entity, request);
    }

    @Override
    protected PurchaseContractRequest normalizeCreateRequest(PurchaseContractRequest request, long entityId) {
        return new PurchaseContractRequest(
                resolveCreateBusinessNo("purchase-contract", request.contractNo(), entityId),
                request.supplierId(),
                request.supplierCode(),
                request.supplierName(),
                request.signDate(),
                request.effectiveDate(),
                request.expireDate(),
                request.buyerName(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected PurchaseContractRequest normalizeUpdateRequest(PurchaseContract entity, PurchaseContractRequest request) {
        return new PurchaseContractRequest(
                entity.getContractNo(),
                request.supplierId(),
                request.supplierCode(),
                request.supplierName(),
                request.signDate(),
                request.effectiveDate(),
                request.expireDate(),
                request.buyerName(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected PurchaseContract newEntity() {
        return new PurchaseContract();
    }

    @Override
    protected void assignId(PurchaseContract entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<PurchaseContract> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<PurchaseContract> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "采购合同不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return StatusConstants.CONTRACT_TRANSITIONS;
    }

    @Override
    protected void apply(PurchaseContract entity, PurchaseContractRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "采购合同状态",
                StatusConstants.ALLOWED_CONTRACT_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "purchase-contract",
                entity.getStatus(),
                nextStatus,
                "已签署",
                "已归档"
        );
        SupplierIdentity supplier = resolveSupplier(request);
        entity.setContractNo(request.contractNo());
        entity.setSupplierId(supplier.id());
        entity.setSupplierCode(supplier.code());
        entity.setSupplierName(supplier.name());
        entity.setSignDate(request.signDate());
        entity.setEffectiveDate(request.effectiveDate());
        entity.setExpireDate(request.expireDate());
        entity.setBuyerName(request.buyerName());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<PurchaseContractItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                PurchaseContractItem::getId,
                PurchaseContractItemRequest::id,
                PurchaseContractItem::new,
                this::nextId,
                PurchaseContractItem::setId
        );

        for (int i = 0; i < request.items().size(); i++) {
            PurchaseContractItemRequest source = request.items().get(i);
            PurchaseContractItem item = items.get(i);
            TradeMaterialSnapshot material = resolveMaterial(source, i + 1);
            item.setPurchaseContract(entity);
            item.setLineNo(i + 1);
            item.setMaterialId(material.materialId());
            item.setMaterialCode(material.materialCode());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setUnit(source.unit());
            item.setQuantity(source.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
            item.setPieceWeightTon(source.pieceWeightTon());
            item.setPiecesPerBundle(source.piecesPerBundle());
            BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon());
            item.setWeightTon(weightTon);
            item.setUnitPrice(source.unitPrice());
            BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.unitPrice());
            item.setAmount(amount);

            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }

        entity.getItems().sort(java.util.Comparator.comparing(PurchaseContractItem::getLineNo));
        entity.setTotalWeight(totalWeight);
        entity.setTotalAmount(totalAmount);
    }

    @Override
    protected PurchaseContract saveEntity(PurchaseContract entity) {
        return repository.save(entity);
    }

    @Override
    protected PurchaseContractResponse toResponse(PurchaseContract entity) {
        return purchaseContractMapper.toResponse(entity);
    }

    @Override
    protected PurchaseContractResponse toSavedResponse(PurchaseContract entity) {
        return toDetailResponse(entity);
    }

    private void assertLineItemsUnchanged(PurchaseContract entity, PurchaseContractRequest request) {
        List<PurchaseContractItem> entityItems = entity.getItems().stream()
                .sorted(java.util.Comparator.comparing(PurchaseContractItem::getLineNo))
                .toList();
        List<PurchaseContractItemRequest> requestItems = request.items() == null ? List.of() : request.items();
        if (entityItems.size() != requestItems.size()) {
            throw readonlyLineItemsChanged();
        }
        for (int i = 0; i < entityItems.size(); i++) {
            if (!matchesExistingLineItem(entityItems.get(i), requestItems.get(i))) {
                throw readonlyLineItemsChanged();
            }
        }
    }

    private boolean matchesExistingLineItem(PurchaseContractItem entityItem, PurchaseContractItemRequest requestItem) {
        return Objects.equals(entityItem.getId(), requestItem.id())
                && Objects.equals(entityItem.getMaterialId(), requestItem.materialId())
                && normalize(entityItem.getMaterialCode()).equals(normalize(requestItem.materialCode()))
                && normalize(entityItem.getBrand()).equals(normalize(requestItem.brand()))
                && normalize(entityItem.getCategory()).equals(normalize(requestItem.category()))
                && normalize(entityItem.getMaterial()).equals(normalize(requestItem.material()))
                && normalize(entityItem.getSpec()).equals(normalize(requestItem.spec()))
                && normalize(entityItem.getLength()).equals(normalize(requestItem.length()))
                && normalize(entityItem.getUnit()).equals(normalize(requestItem.unit()))
                && Objects.equals(entityItem.getQuantity(), requestItem.quantity())
                && TradeItemCalculator.normalizeQuantityUnit(entityItem.getQuantityUnit())
                .equals(TradeItemCalculator.normalizeQuantityUnit(requestItem.quantityUnit()))
                && compareWeight(entityItem.getPieceWeightTon(), requestItem.pieceWeightTon())
                && Objects.equals(entityItem.getPiecesPerBundle(), requestItem.piecesPerBundle())
                && compareWeight(entityItem.getWeightTon(), requestItem.weightTon())
                && compareAmount(entityItem.getUnitPrice(), requestItem.unitPrice())
                && compareAmount(entityItem.getAmount(), requestItem.amount());
    }

    private boolean compareWeight(BigDecimal left, BigDecimal right) {
        return TradeItemCalculator.scaleWeightTon(left).compareTo(TradeItemCalculator.scaleWeightTon(right)) == 0;
    }

    private boolean compareAmount(BigDecimal left, BigDecimal right) {
        return TradeItemCalculator.scaleAmount(left).compareTo(TradeItemCalculator.scaleAmount(right)) == 0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private BusinessException readonlyLineItemsChanged() {
        return new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "采购合同明细不允许编辑");
    }

    private SupplierIdentity resolveSupplier(PurchaseContractRequest request) {
        if (supplierRepository == null) {
            return new SupplierIdentity(request.supplierId(), normalizeNullable(request.supplierCode()),
                    normalizeNullable(request.supplierName()));
        }
        Supplier supplier = findSupplier(request);
        String requestedCode = normalizeNullable(request.supplierCode());
        String requestedName = normalizeNullable(request.supplierName());
        if (requestedCode != null && !Objects.equals(requestedCode, normalizeNullable(supplier.getSupplierCode()))) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "供应商ID与供应商编码不一致");
        }
        if (requestedName != null && !Objects.equals(requestedName, normalizeNullable(supplier.getSupplierName()))) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "供应商ID与供应商名称不一致");
        }
        return new SupplierIdentity(supplier.getId(), supplier.getSupplierCode(), supplier.getSupplierName());
    }

    private Supplier findSupplier(PurchaseContractRequest request) {
        if (request.supplierId() != null) {
            return supplierRepository.findByIdAndDeletedFlagFalse(request.supplierId())
                    .orElseThrow(this::supplierNotFound);
        }
        String supplierCode = normalizeNullable(request.supplierCode());
        if (supplierCode != null) {
            Supplier supplier = supplierRepository.findBySupplierCodeAndDeletedFlagFalse(supplierCode)
                    .orElseThrow(this::supplierNotFound);
            log.warn("identity_fallback module=purchase-contract field=supplierId reason=supplier-code resolvedId={}",
                    supplier.getId());
            return supplier;
        }
        List<Supplier> candidates = supplierRepository.findBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(
                normalize(request.supplierName())
        );
        if (candidates.size() != 1) {
            if (candidates.isEmpty()) {
                throw supplierNotFound();
            }
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "供应商名称对应多个编码，请选择供应商ID");
        }
        log.warn("identity_fallback module=purchase-contract field=supplierId reason=supplier-name resolvedId={}",
                candidates.get(0).getId());
        return candidates.get(0);
    }

    private BusinessException supplierNotFound() {
        return new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "供应商不存在，请先在主数据供应商资料中维护");
    }

    private TradeMaterialSnapshot resolveMaterial(PurchaseContractItemRequest source, int lineNo) {
        if (tradeItemMaterialSupport == null) {
            return new TradeMaterialSnapshot(source.materialId(), source.materialCode(), false);
        }
        return tradeItemMaterialSupport.resolveMaterial(source.materialId(), source.materialCode(), lineNo);
    }

    private String normalizeNullable(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private record SupplierIdentity(Long id, String code, String name) {
    }
}
