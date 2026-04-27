package com.leo.erp.contract.sales.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.ManagedEntityItemSupport;
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
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class SalesContractService extends AbstractCrudService<SalesContract, SalesContractRequest, SalesContractResponse> {

    private final SalesContractRepository repository;
    private final SalesContractMapper salesContractMapper;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public SalesContractService(SalesContractRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                SalesContractMapper salesContractMapper,
                                WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.salesContractMapper = salesContractMapper;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    public Page<SalesContractResponse> page(PageQuery query,
                                            String keyword,
                                            String customerName,
                                            String status,
                                            java.time.LocalDate startDate,
                                            java.time.LocalDate endDate) {
        Specification<SalesContract> spec = Specs.<SalesContract>notDeleted()
                .and(Specs.keywordLike(keyword, "contractNo", "customerName", "projectName"))
                .and(Specs.equalIfPresent("customerName", customerName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("signDate", startDate, endDate));
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
        String nextStatus = (request.status() == null || request.status().isBlank()) ? "草稿" : request.status();
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "sales-contracts",
                entity.getStatus(),
                nextStatus,
                "已签署",
                "已归档"
        );
        entity.setContractNo(request.contractNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setSignDate(request.signDate());
        entity.setEffectiveDate(request.effectiveDate());
        entity.setExpireDate(request.expireDate());
        entity.setSalesName(request.salesName());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<SalesContractItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                SalesContractItem::getId,
                SalesContractItemRequest::id,
                SalesContractItem::new,
                this::nextId,
                SalesContractItem::setId
        );

        for (int i = 0; i < request.items().size(); i++) {
            SalesContractItemRequest source = request.items().get(i);
            SalesContractItem item = items.get(i);
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

            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }

        entity.getItems().sort(java.util.Comparator.comparing(SalesContractItem::getLineNo));
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
