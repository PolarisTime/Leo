package com.leo.erp.contract.purchase.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.contract.purchase.domain.entity.PurchaseContract;
import com.leo.erp.contract.purchase.domain.entity.PurchaseContractItem;
import com.leo.erp.contract.purchase.repository.PurchaseContractRepository;
import com.leo.erp.contract.purchase.mapper.PurchaseContractMapper;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractRequest;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractResponse;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractItemRequest;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PurchaseContractService extends AbstractCrudService<PurchaseContract, PurchaseContractRequest, PurchaseContractResponse> {

    private final PurchaseContractRepository repository;
    private final PurchaseContractMapper purchaseContractMapper;

    public PurchaseContractService(PurchaseContractRepository repository,
                                   SnowflakeIdGenerator idGenerator,
                                   PurchaseContractMapper purchaseContractMapper) {
        super(idGenerator);
        this.repository = repository;
        this.purchaseContractMapper = purchaseContractMapper;
    }

    public Page<PurchaseContractResponse> page(PageQuery query, String keyword) {
        Specification<PurchaseContract> spec = Specs.<PurchaseContract>notDeleted()
                .and(Specs.keywordLike(keyword, "contractNo", "supplierName", "buyerName"));
        return page(query, spec, repository);
    }

    @Override
    protected PurchaseContractResponse toDetailResponse(PurchaseContract entity) {
        PurchaseContractResponse response = purchaseContractMapper.toResponse(entity);
        return new PurchaseContractResponse(
                response.id(), response.contractNo(), response.supplierName(),
                response.signDate(), response.effectiveDate(), response.expireDate(),
                response.buyerName(), response.totalWeight(), response.totalAmount(),
                response.status(), response.remark(),
                entity.getItems().stream().map(item -> new PurchaseContractItemResponse(
                        item.getId(), item.getLineNo(), item.getMaterialCode(),
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
    protected String notFoundMessage() {
        return "采购合同不存在";
    }

    @Override
    protected void apply(PurchaseContract entity, PurchaseContractRequest request) {
        entity.setContractNo(request.contractNo());
        entity.setSupplierName(request.supplierName());
        entity.setSignDate(request.signDate());
        entity.setEffectiveDate(request.effectiveDate());
        entity.setExpireDate(request.expireDate());
        entity.setBuyerName(request.buyerName());
        entity.setStatus((request.status() == null || request.status().isBlank()) ? "草稿" : request.status());
        entity.setRemark(request.remark());

        entity.getItems().clear();
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<PurchaseContractItem> items = new ArrayList<>();

        for (int i = 0; i < request.items().size(); i++) {
            PurchaseContractItemRequest source = request.items().get(i);
            PurchaseContractItem item = new PurchaseContractItem();
            item.setId(nextId());
            item.setPurchaseContract(entity);
            item.setLineNo(i + 1);
            item.setMaterialCode(source.materialCode());
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
            items.add(item);

            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }

        entity.getItems().addAll(items);
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
}
