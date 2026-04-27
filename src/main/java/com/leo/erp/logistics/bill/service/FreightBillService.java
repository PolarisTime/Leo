package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.logistics.bill.mapper.FreightBillMapper;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.logistics.bill.web.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class FreightBillService extends AbstractCrudService<FreightBill, FreightBillRequest, FreightBillResponse> {

    private final FreightBillRepository repository;
    private final FreightBillMapper freightBillMapper;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public FreightBillService(FreightBillRepository repository,
                              SnowflakeIdGenerator idGenerator,
                              FreightBillMapper freightBillMapper,
                              WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.freightBillMapper = freightBillMapper;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    public Page<FreightBillResponse> page(PageQuery query,
                                          String keyword,
                                          String carrierName,
                                          String status,
                                          java.time.LocalDate startDate,
                                          java.time.LocalDate endDate) {
        Specification<FreightBill> spec = Specs.<FreightBill>notDeleted()
                .and(Specs.keywordLike(keyword, "billNo", "carrierName", "customerName"))
                .and(Specs.equalIfPresent("carrierName", carrierName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("billTime", startDate, endDate));
        return page(query, spec, repository);
    }

    @Override
    protected FreightBillResponse toDetailResponse(FreightBill entity) {
        FreightBillResponse response = freightBillMapper.toResponse(entity);
        return new FreightBillResponse(
                response.id(), response.billNo(), response.outboundNo(),
                response.carrierName(), response.customerName(), response.projectName(),
                response.billTime(), response.unitPrice(), response.totalWeight(),
                response.totalFreight(), response.status(), response.deliveryStatus(),
                response.remark(),
                entity.getItems().stream().map(item -> new FreightBillItemResponse(
                        item.getId(), item.getLineNo(), item.getSourceNo(),
                        item.getCustomerName(), item.getProjectName(), item.getMaterialCode(),
                        item.getMaterialName(), item.getBrand(), item.getCategory(),
                        item.getMaterial(), item.getSpec(), item.getLength(),
                        item.getQuantity(), item.getQuantityUnit(), item.getPieceWeightTon(), item.getPiecesPerBundle(),
                        item.getBatchNo(), item.getWeightTon(), item.getWarehouseName()
                )).toList()
        );
    }

    @Override
    protected void validateCreate(FreightBillRequest request) {
        if (repository.existsByBillNoAndDeletedFlagFalse(request.billNo())) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "物流单号已存在");
        }
    }

    @Override
    protected void validateUpdate(FreightBill entity, FreightBillRequest request) {
        if (!entity.getBillNo().equals(request.billNo()) && repository.existsByBillNoAndDeletedFlagFalse(request.billNo())) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "物流单号已存在");
        }
    }

    @Override
    protected FreightBill newEntity() {
        return new FreightBill();
    }

    @Override
    protected void assignId(FreightBill entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<FreightBill> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "物流单不存在";
    }

    @Override
    protected void apply(FreightBill entity, FreightBillRequest request) {
        String nextStatus = (request.status() == null || request.status().isBlank()) ? "未审核" : request.status();
        String nextDeliveryStatus = (request.deliveryStatus() == null || request.deliveryStatus().isBlank()) ? "未送达" : request.deliveryStatus();
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "freight-bills",
                entity.getStatus(),
                nextStatus,
                "已审核"
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "freight-bills",
                entity.getDeliveryStatus(),
                nextDeliveryStatus,
                "已送达"
        );
        entity.setBillNo(request.billNo());
        entity.setOutboundNo(request.outboundNo());
        entity.setCarrierName(request.carrierName());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setBillTime(request.billTime());
        entity.setUnitPrice(request.unitPrice());
        entity.setStatus(nextStatus);
        entity.setDeliveryStatus(nextDeliveryStatus);
        entity.setRemark(request.remark());

        BigDecimal totalWeight = BigDecimal.ZERO;
        List<FreightBillItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                FreightBillItem::getId,
                FreightBillItemRequest::id,
                FreightBillItem::new,
                this::nextId,
                FreightBillItem::setId
        );
        for (int i = 0; i < request.items().size(); i++) {
            FreightBillItemRequest source = request.items().get(i);
            FreightBillItem item = items.get(i);
            item.setFreightBill(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(source.sourceNo());
            item.setCustomerName(source.customerName());
            item.setProjectName(source.projectName());
            item.setMaterialCode(source.materialCode());
            item.setMaterialName(source.materialName());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setQuantity(source.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
            item.setPieceWeightTon(source.pieceWeightTon());
            item.setPiecesPerBundle(source.piecesPerBundle());
            item.setBatchNo(source.batchNo());
            BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon());
            item.setWeightTon(weightTon);
            item.setWarehouseName(source.warehouseName());
            totalWeight = totalWeight.add(weightTon);
        }
        entity.getItems().sort(java.util.Comparator.comparing(FreightBillItem::getLineNo));
        entity.setTotalWeight(totalWeight);
        entity.setTotalFreight(totalWeight.multiply(request.unitPrice()).setScale(2, RoundingMode.HALF_UP));
    }

    @Override
    protected FreightBill saveEntity(FreightBill entity) {
        return repository.save(entity);
    }

    @Override
    protected FreightBillResponse toResponse(FreightBill entity) {
        return freightBillMapper.toResponse(entity);
    }
}
