package com.leo.erp.contract.sales.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.contract.sales.domain.entity.SalesContract;
import com.leo.erp.contract.sales.domain.entity.SalesContractItem;
import com.leo.erp.contract.sales.repository.SalesContractRepository;
import com.leo.erp.contract.sales.mapper.SalesContractMapper;
import com.leo.erp.contract.sales.web.dto.SalesContractRequest;
import com.leo.erp.contract.sales.web.dto.SalesContractResponse;
import com.leo.erp.contract.sales.web.dto.SalesContractItemRequest;
import com.leo.erp.contract.sales.web.dto.SalesContractItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SalesContractService extends AbstractCrudService<SalesContract, SalesContractRequest, SalesContractResponse> {

    private final SalesContractRepository repository;
    private final SalesContractMapper salesContractMapper;

    public SalesContractService(SalesContractRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                SalesContractMapper salesContractMapper) {
        super(idGenerator);
        this.repository = repository;
        this.salesContractMapper = salesContractMapper;
    }

    public Page<SalesContractResponse> page(PageQuery query, String keyword) {
        Specification<SalesContract> spec = Specs.<SalesContract>notDeleted()
                .and(Specs.keywordLike(keyword, "contractNo", "customerName", "projectName"));
        return page(query, spec, repository);
    }

    @Override
    protected SalesContractResponse toDetailResponse(SalesContract entity) {
        SalesContractResponse response = salesContractMapper.toResponse(entity);
        return new SalesContractResponse(
                response.id(), response.contractNo(), response.customerName(),
                response.projectName(), response.signDate(), response.effectiveDate(),
                response.expireDate(), response.salesName(), response.totalWeight(),
                response.totalAmount(), response.status(), response.remark(),
                entity.getItems().stream().map(item -> new SalesContractItemResponse(
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
    protected void validateCreate(SalesContractRequest request) {
        if (repository.existsByContractNoAndDeletedFlagFalse(request.contractNo())) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "销售合同号已存在");
        }
    }

    @Override
    protected void validateUpdate(SalesContract entity, SalesContractRequest request) {
        if (!entity.getContractNo().equals(request.contractNo())
                && repository.existsByContractNoAndDeletedFlagFalse(request.contractNo())) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "销售合同号已存在");
        }
    }

    @Override
    protected SalesContract newEntity() {
        return new SalesContract();
    }

    @Override
    protected void assignId(SalesContract entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<SalesContract> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "销售合同不存在";
    }

    @Override
    protected void apply(SalesContract entity, SalesContractRequest request) {
        entity.setContractNo(request.contractNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setSignDate(request.signDate());
        entity.setEffectiveDate(request.effectiveDate());
        entity.setExpireDate(request.expireDate());
        entity.setSalesName(request.salesName());
        entity.setStatus((request.status() == null || request.status().isBlank()) ? "草稿" : request.status());
        entity.setRemark(request.remark());

        entity.getItems().clear();
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<SalesContractItem> items = new ArrayList<>();

        for (int i = 0; i < request.items().size(); i++) {
            SalesContractItemRequest source = request.items().get(i);
            SalesContractItem item = new SalesContractItem();
            item.setId(nextId());
            item.setSalesContract(entity);
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
    protected SalesContract saveEntity(SalesContract entity) {
        return repository.save(entity);
    }

    @Override
    protected SalesContractResponse toResponse(SalesContract entity) {
        return salesContractMapper.toResponse(entity);
    }
}
