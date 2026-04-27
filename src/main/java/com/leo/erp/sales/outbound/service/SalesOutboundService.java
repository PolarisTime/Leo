package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.mapper.SalesOutboundMapper;
import com.leo.erp.sales.outbound.web.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SalesOutboundService extends AbstractCrudService<SalesOutbound, SalesOutboundRequest, SalesOutboundResponse> {

    private final SalesOutboundRepository repository;
    private final SalesOutboundMapper salesOutboundMapper;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final WarehouseSelectionSupport warehouseSelectionSupport;

    public SalesOutboundService(SalesOutboundRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                SalesOutboundMapper salesOutboundMapper,
                                TradeItemMaterialSupport tradeItemMaterialSupport,
                                WarehouseSelectionSupport warehouseSelectionSupport) {
        super(idGenerator);
        this.repository = repository;
        this.salesOutboundMapper = salesOutboundMapper;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
    }

    @Transactional(readOnly = true)
    public Page<SalesOutboundResponse> page(PageQuery query, String keyword) {
        Specification<SalesOutbound> spec = Specs.<SalesOutbound>notDeleted()
                .and(Specs.keywordLike(keyword, "outboundNo", "salesOrderNo", "customerName"));
        return page(query, spec, repository);
    }

    @Override
    protected SalesOutboundResponse toDetailResponse(SalesOutbound entity) {
        SalesOutboundResponse response = salesOutboundMapper.toResponse(entity);
        return new SalesOutboundResponse(
                response.id(), response.outboundNo(), response.salesOrderNo(),
                response.customerName(), response.projectName(), response.warehouseName(),
                response.outboundDate(), response.totalWeight(), response.totalAmount(),
                response.status(), response.remark(),
                entity.getItems().stream().map(item -> new SalesOutboundItemResponse(
                        item.getId(), item.getLineNo(), item.getMaterialCode(),
                        item.getBrand(), item.getCategory(), item.getMaterial(),
                        item.getSpec(), item.getLength(), item.getUnit(), item.getWarehouseName(), item.getBatchNo(),
                        item.getQuantity(), item.getQuantityUnit(), item.getPieceWeightTon(), item.getPiecesPerBundle(),
                        item.getWeightTon(), item.getUnitPrice(), item.getAmount()
                )).toList()
        );
    }

    @Override
    protected void validateCreate(SalesOutboundRequest request) {
        if (repository.existsByOutboundNoAndDeletedFlagFalse(request.outboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库单号已存在");
        }
    }

    @Override
    protected void validateUpdate(SalesOutbound entity, SalesOutboundRequest request) {
        if (!entity.getOutboundNo().equals(request.outboundNo()) && repository.existsByOutboundNoAndDeletedFlagFalse(request.outboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库单号已存在");
        }
    }

    @Override
    protected SalesOutbound newEntity() {
        return new SalesOutbound();
    }

    @Override
    protected void assignId(SalesOutbound entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<SalesOutbound> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "销售出库不存在";
    }

    @Override
    protected void apply(SalesOutbound entity, SalesOutboundRequest request) {
        entity.setOutboundNo(request.outboundNo());
        entity.setSalesOrderNo(request.salesOrderNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setWarehouseName(request.warehouseName());
        entity.setOutboundDate(request.outboundDate());
        entity.setStatus((request.status() == null || request.status().isBlank()) ? "草稿" : request.status());
        entity.setRemark(request.remark());

        entity.getItems().clear();
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<SalesOutboundItem> items = new ArrayList<>();
        var materialMap = tradeItemMaterialSupport.loadMaterialMap(
                request.items().stream().map(SalesOutboundItemRequest::materialCode).toList()
        );
        for (int i = 0; i < request.items().size(); i++) {
            SalesOutboundItemRequest source = request.items().get(i);
            Material material = materialMap.get(source.materialCode());
            SalesOutboundItem item = new SalesOutboundItem();
            item.setId(nextId());
            item.setSalesOutbound(entity);
            item.setLineNo(i + 1);
            item.setMaterialCode(source.materialCode());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setUnit(source.unit());
            item.setWarehouseName(warehouseSelectionSupport.normalizeWarehouseName(
                    source.warehouseName() == null || source.warehouseName().isBlank() ? request.warehouseName() : source.warehouseName(),
                    i + 1,
                    true
            ));
            item.setBatchNo(tradeItemMaterialSupport.normalizeBatchNo(material, source.batchNo(), i + 1, true));
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
    protected SalesOutbound saveEntity(SalesOutbound entity) {
        return repository.save(entity);
    }

    @Override
    protected SalesOutboundResponse toResponse(SalesOutbound entity) {
        return salesOutboundMapper.toResponse(entity);
    }
}
