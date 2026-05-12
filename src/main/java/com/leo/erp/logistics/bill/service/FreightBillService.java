package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        Specification<FreightBill> spec = Specs.<FreightBill>keywordLike(keyword, "billNo", "carrierName", "customerName")
                .and(Specs.equalIfPresent("carrierName", carrierName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("billTime", startDate, endDate));
        return page(query, spec, repository);
    }

    private static final String[] FREIGHT_BILL_SEARCH_FIELDS = {
            "billNo",
            "carrierName",
            "customerName"
    };

    public java.util.List<FreightBillResponse> search(String keyword, int maxSize) {
        return search(keyword, FREIGHT_BILL_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected FreightBillResponse toDetailResponse(FreightBill entity) {
        FreightBillResponse response = freightBillMapper.toResponse(entity);
        return new FreightBillResponse(
                response.id(), response.billNo(), response.outboundNo(),
                response.carrierName(), response.vehiclePlate(),
                response.customerName(), response.projectName(),
                response.billTime(), response.unitPrice(), response.totalWeight(),
                response.totalFreight(), response.status(), response.deliveryStatus(),
                response.remark(),
                entity.getItems().stream().map(item -> new FreightBillItemResponse(
                        item.getId(), item.getLineNo(), item.getSourceNo(),
                        item.getCustomerName(), item.getProjectName(), item.getMaterialCode(),
                        resolveMaterialName(item), item.getBrand(), item.getCategory(),
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
    protected FreightBillRequest normalizeCreateRequest(FreightBillRequest request, long entityId) {
        return new FreightBillRequest(
                resolveCreateBusinessNo("freight-bill", request.billNo(), entityId),
                request.outboundNo(),
                request.carrierName(),
                request.vehiclePlate(),
                request.customerName(),
                request.projectName(),
                request.billTime(),
                request.unitPrice(),
                request.status(),
                request.deliveryStatus(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected FreightBillRequest normalizeUpdateRequest(FreightBill entity, FreightBillRequest request) {
        return new FreightBillRequest(
                entity.getBillNo(),
                request.outboundNo(),
                request.carrierName(),
                request.vehiclePlate(),
                request.customerName(),
                request.projectName(),
                request.billTime(),
                request.unitPrice(),
                request.status(),
                request.deliveryStatus(),
                request.remark(),
                request.items()
        );
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
    protected Optional<FreightBill> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "物流单不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected void apply(FreightBill entity, FreightBillRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.UNAUDITED,
                "物流单状态",
                StatusConstants.ALLOWED_FREIGHT_BILL_STATUS
        );
        String nextDeliveryStatus = BusinessStatusValidator.normalizeWithDefault(
                request.deliveryStatus(),
                StatusConstants.UNDELIVERED,
                "物流单送达状态",
                StatusConstants.ALLOWED_DELIVERY_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "freight-bill",
                entity.getStatus(),
                nextStatus,
                StatusConstants.AUDITED
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "freight-bill",
                entity.getDeliveryStatus(),
                nextDeliveryStatus,
                StatusConstants.DELIVERED
        );
        entity.setBillNo(request.billNo());
        entity.setCarrierName(request.carrierName());
        entity.setVehiclePlate(emptyToNull(request.vehiclePlate()));
        entity.setBillTime(request.billTime());
        entity.setUnitPrice(request.unitPrice());
        entity.setStatus(nextStatus);
        entity.setDeliveryStatus(nextDeliveryStatus);
        entity.setRemark(request.remark());

        assertSourceOutboundsNotOccupied(request, entity.getId());

        BigDecimal totalWeight = BigDecimal.ZERO;
        LinkedHashSet<String> sourceNos = new LinkedHashSet<>();
        LinkedHashSet<String> customerNames = new LinkedHashSet<>();
        LinkedHashSet<String> projectNames = new LinkedHashSet<>();
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
            sourceNos.add(source.sourceNo());
            item.setCustomerName(source.customerName());
            customerNames.add(source.customerName());
            item.setProjectName(source.projectName());
            projectNames.add(source.projectName());
            item.setMaterialCode(source.materialCode());
            item.setMaterialName(resolveMaterialName(source));
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
        entity.setOutboundNo(String.join(", ", sourceNos));
        entity.setCustomerName(resolveHeaderLabel(customerNames, "多客户"));
        entity.setProjectName(resolveHeaderLabel(projectNames, "多项目"));
        entity.setTotalWeight(totalWeight);
        entity.setTotalFreight(totalWeight.multiply(request.unitPrice()).setScale(2, RoundingMode.HALF_UP));
    }

    private void assertSourceOutboundsNotOccupied(FreightBillRequest request, Long currentBillId) {
        Set<String> sourceNos = request.items().stream()
                .map(FreightBillItemRequest::sourceNo)
                .map(this::emptyToNull)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (sourceNos.isEmpty()) {
            return;
        }

        List<FreightBill> occupiedBills = repository.findAllBySourceNosExcludingCurrentBill(sourceNos, currentBillId);
        for (String sourceNo : sourceNos) {
            for (FreightBill occupiedBill : occupiedBills) {
                boolean matched = occupiedBill.getItems().stream()
                        .anyMatch(item -> sourceNo.equals(emptyToNull(item.getSourceNo())));
                if (!matched) {
                    continue;
                }
                String billNo = emptyToNull(occupiedBill.getBillNo());
                String carrierName = emptyToNull(occupiedBill.getCarrierName());
                throw new BusinessException(
                        com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                        "销售出库单" + sourceNo + "已归集到物流单"
                                + (billNo == null ? "" : billNo)
                                + (carrierName == null ? "" : "（物流商：" + carrierName + "）")
                );
            }
        }
    }

    private String resolveHeaderLabel(Set<String> values, String multipleLabel) {
        if (values.isEmpty()) {
            return multipleLabel;
        }
        if (values.size() == 1) {
            return values.iterator().next();
        }
        return multipleLabel;
    }

    private String emptyToNull(String value) {
        return value == null ? null : value.trim().isEmpty() ? null : value.trim();
    }

    private String resolveMaterialName(FreightBillItemRequest source) {
        String explicitName = emptyToNull(source.materialName());
        if (explicitName != null) {
            return explicitName;
        }
        return emptyToNull(source.brand());
    }

    private String resolveMaterialName(FreightBillItem item) {
        String explicitName = emptyToNull(item.getMaterialName());
        if (explicitName != null) {
            return explicitName;
        }
        return emptyToNull(item.getBrand());
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
