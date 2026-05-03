package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.mapper.SalesOutboundMapper;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.outbound.web.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class SalesOutboundService extends AbstractCrudService<SalesOutbound, SalesOutboundRequest, SalesOutboundResponse> {

    private final SalesOutboundRepository repository;
    private final SalesOutboundMapper salesOutboundMapper;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final WarehouseSelectionSupport warehouseSelectionSupport;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public SalesOutboundService(SalesOutboundRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                SalesOutboundMapper salesOutboundMapper,
                                TradeItemMaterialSupport tradeItemMaterialSupport,
                                WarehouseSelectionSupport warehouseSelectionSupport,
                                WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.salesOutboundMapper = salesOutboundMapper;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<SalesOutboundResponse> page(PageQuery query,
                                            String keyword,
                                            String customerName,
                                            String status,
                                            java.time.LocalDate startDate,
                                            java.time.LocalDate endDate) {
        Specification<SalesOutbound> spec = Specs.<SalesOutbound>notDeleted()
                .and(Specs.keywordLike(keyword, "outboundNo", "salesOrderNo", "customerName"))
                .and(Specs.equalIfPresent("customerName", customerName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("outboundDate", startDate, endDate));
        return page(query, spec, repository);
    }

    private static final String[] OUTBOUND_SEARCH_FIELDS = {"outboundNo", "salesOrderNo", "customerName"};

    @Transactional(readOnly = true)
    public java.util.List<SalesOutboundResponse> search(String keyword, int maxSize) {
        return search(keyword, OUTBOUND_SEARCH_FIELDS, maxSize, Specs.notDeleted(), repository);
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
        String nextStatus = (request.status() == null || request.status().isBlank()) ? StatusConstants.DRAFT : request.status();
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "sales-outbounds",
                entity.getStatus(),
                nextStatus,
                StatusConstants.AUDITED
        );
        entity.setOutboundNo(request.outboundNo());
        entity.setSalesOrderNo(request.salesOrderNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setOutboundDate(request.outboundDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        String firstLineWarehouseName = null;
        var materialMap = tradeItemMaterialSupport.loadMaterialMap(
                request.items().stream().map(SalesOutboundItemRequest::materialCode).toList()
        );
        List<SalesOutboundItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                SalesOutboundItem::getId,
                SalesOutboundItemRequest::id,
                SalesOutboundItem::new,
                this::nextId,
                SalesOutboundItem::setId
        );
        for (int i = 0; i < request.items().size(); i++) {
            SalesOutboundItemRequest source = request.items().get(i);
            Material material = materialMap.get(source.materialCode());
            SalesOutboundItem item = items.get(i);
            item.setSalesOutbound(entity);
            item.setLineNo(i + 1);
            item.setMaterialCode(source.materialCode());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setUnit(source.unit());
            String warehouseName = warehouseSelectionSupport.normalizeWarehouseName(
                    source.warehouseName() == null || source.warehouseName().isBlank() ? request.warehouseName() : source.warehouseName(),
                    i + 1,
                    true
            );
            if (firstLineWarehouseName == null) {
                firstLineWarehouseName = warehouseName;
            }
            item.setWarehouseName(warehouseName);
            item.setBatchNo(tradeItemMaterialSupport.normalizeBatchNo(material, source.batchNo(), i + 1, true));
            item.setQuantity(source.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
            item.setPieceWeightTon(source.pieceWeightTon());
            item.setPiecesPerBundle(source.piecesPerBundle());
            BigDecimal weightTon = source.weightTon() == null
                    ? TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon())
                    : TradeItemCalculator.scaleWeightTon(source.weightTon());
            item.setWeightTon(weightTon);
            item.setUnitPrice(source.unitPrice());
            BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.unitPrice());
            item.setAmount(amount);
            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(SalesOutboundItem::getLineNo));
        entity.setWarehouseName(firstLineWarehouseName == null ? trimToNull(request.warehouseName()) : firstLineWarehouseName);
        entity.setTotalWeight(totalWeight);
        entity.setTotalAmount(totalAmount);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
