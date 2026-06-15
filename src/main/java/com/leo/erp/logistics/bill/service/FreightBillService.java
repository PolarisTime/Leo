package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.logistics.bill.mapper.FreightBillMapper;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.logistics.bill.web.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class FreightBillService extends AbstractCrudService<FreightBill, FreightBillRequest, FreightBillResponse> {

    private final FreightBillRepository repository;
    private final FreightBillMapper freightBillMapper;
    private final FreightBillSourceService freightBillSourceService;
    private final FreightBillApplyService freightBillApplyService;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    @Autowired
    public FreightBillService(FreightBillRepository repository,
                              SnowflakeIdGenerator idGenerator,
                              FreightBillMapper freightBillMapper,
                              FreightBillSourceService freightBillSourceService,
                              FreightBillApplyService freightBillApplyService,
                              WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.freightBillMapper = freightBillMapper;
        this.freightBillSourceService = freightBillSourceService;
        this.freightBillApplyService = freightBillApplyService;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    public Page<FreightBillResponse> page(PageQuery query, PageFilter filter) {
        Specification<FreightBill> spec = Specs.<FreightBill>keywordLike(filter.keyword(), "billNo", "carrierName", "customerName")
                .and(Specs.equalIfPresent("carrierName", filter.name()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("billTime", filter.startDate(), filter.endDate()));
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
                response.id(), response.billNo(),
                response.carrierName(), response.vehiclePlate(),
                response.customerName(), response.projectName(),
                response.billTime(), response.unitPrice(), response.totalWeight(),
                response.totalFreight(), response.status(),
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
                request.carrierName(),
                request.vehiclePlate(),
                request.customerName(),
                request.projectName(),
                request.billTime(),
                request.unitPrice(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected FreightBillRequest normalizeUpdateRequest(FreightBill entity, FreightBillRequest request) {
        return new FreightBillRequest(
                entity.getBillNo(),
                request.carrierName(),
                request.vehiclePlate(),
                request.customerName(),
                request.projectName(),
                request.billTime(),
                request.unitPrice(),
                request.status(),
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
    protected java.util.Set<String> allowedStatusTransitions() {
        return StatusConstants.FREIGHT_BILL_AUDIT_TRANSITIONS;
    }

    @Override
    protected void apply(FreightBill entity, FreightBillRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.UNAUDITED,
                "物流单状态",
                StatusConstants.ALLOWED_FREIGHT_BILL_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "freight-bill",
                entity.getStatus(),
                nextStatus,
                StatusConstants.AUDITED
        );
        entity.setBillNo(request.billNo());
        entity.setCarrierName(request.carrierName());
        entity.setVehiclePlate(emptyToNull(request.vehiclePlate()));
        entity.setBillTime(request.billTime());
        entity.setUnitPrice(request.unitPrice());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        freightBillSourceService.validateSources(request, entity.getId());
        freightBillApplyService.applyItems(entity, request, this::nextId);
    }

    private String emptyToNull(String value) {
        return BusinessDocumentValidator.trimToNull(value);
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

    @Override
    protected FreightBillResponse toSavedResponse(FreightBill entity) {
        return toDetailResponse(entity);
    }
}
