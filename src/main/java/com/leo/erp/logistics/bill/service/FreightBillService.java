package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.mapper.FreightBillMapper;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.logistics.bill.web.dto.*;
import jakarta.persistence.criteria.Join;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class FreightBillService extends AbstractCrudService<FreightBill, FreightBillRequest, FreightBillResponse> {

    private final FreightBillRepository repository;
    private final SalesOutboundRepository salesOutboundRepository;
    private final FreightBillMapper freightBillMapper;
    private final FreightBillSourceService freightBillSourceService;
    private final FreightBillApplyService freightBillApplyService;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final CarrierRepository carrierRepository;

    public FreightBillService(FreightBillRepository repository,
                              SalesOutboundRepository salesOutboundRepository,
                              SnowflakeIdGenerator idGenerator,
                              FreightBillMapper freightBillMapper,
                              FreightBillSourceService freightBillSourceService,
                              FreightBillApplyService freightBillApplyService,
                              WorkflowTransitionGuard workflowTransitionGuard) {
        this(repository, salesOutboundRepository, idGenerator, freightBillMapper, freightBillSourceService,
                freightBillApplyService, workflowTransitionGuard, null);
    }

    @Autowired
    public FreightBillService(FreightBillRepository repository,
                              SalesOutboundRepository salesOutboundRepository,
                              SnowflakeIdGenerator idGenerator,
                              FreightBillMapper freightBillMapper,
                              FreightBillSourceService freightBillSourceService,
                              FreightBillApplyService freightBillApplyService,
                              WorkflowTransitionGuard workflowTransitionGuard,
                              CarrierRepository carrierRepository) {
        super(idGenerator);
        this.repository = repository;
        this.salesOutboundRepository = salesOutboundRepository;
        this.freightBillMapper = freightBillMapper;
        this.freightBillSourceService = freightBillSourceService;
        this.freightBillApplyService = freightBillApplyService;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.carrierRepository = carrierRepository;
    }

    public Page<FreightBillResponse> page(PageQuery query, PageFilter filter) {
        Specification<FreightBill> spec = Specs.<FreightBill>keywordLike(filter.keyword(), "billNo", "carrierName", "customerName")
                .and(Specs.equalIfPresent("carrierName", filter.name()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("billTime", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    @Transactional(readOnly = true)
    public Page<FreightBillImportCandidateResponse> importCandidates(PageQuery query, PageFilter filter) {
        Specification<SalesOutbound> spec = Specs.<SalesOutbound>notDeleted()
                .and(Specs.keywordLike(filter.keyword(), "outboundNo", "salesOrderNo", "customerName", "projectName"))
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(importableSalesOutboundStatus(filter.status()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.betweenIfPresent("outboundDate", filter.startDate(), filter.endDate()))
                .and(sourceOutboundNotOccupied());
        return salesOutboundRepository.findAll(DataScopeContext.apply(spec), query.toPageable("id"))
                .map(this::toImportCandidateResponse);
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
                response.carrierName(), response.settlementCompanyId(), response.settlementCompanyName(), response.vehiclePlate(),
                response.customerName(), response.projectName(),
                response.billTime(), response.unitPrice(), response.totalWeight(),
                response.totalFreight(), response.status(),
                response.remark(),
                entity.getItems().stream().map(item -> new FreightBillItemResponse(
                        item.getId(), item.getLineNo(), item.getSourceNo(),
                        item.getSourceSalesOutboundItemId(),
                        item.getSettlementCompanyId(), item.getSettlementCompanyName(),
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
        applyCarrierSettlementCompany(entity, request.carrierName());
        entity.setVehiclePlate(emptyToNull(request.vehiclePlate()));
        entity.setBillTime(request.billTime());
        entity.setUnitPrice(request.unitPrice());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        FreightBillSourceService.SourceValidationContext sourceContext =
                freightBillSourceService.validateSources(request, entity.getId());
        freightBillApplyService.applyItems(entity, request, sourceContext, this::nextId);
    }

    private void applyCarrierSettlementCompany(FreightBill entity, String carrierName) {
        if (carrierRepository == null) {
            return;
        }
        String normalizedCarrierName = emptyToNull(carrierName);
        if (normalizedCarrierName == null) {
            entity.setSettlementCompanyId(null);
            entity.setSettlementCompanyName(null);
            return;
        }
        Carrier carrier = carrierRepository.findFirstByCarrierNameAndDeletedFlagFalseOrderByCarrierCodeAsc(normalizedCarrierName)
                .orElse(null);
        if (carrier == null) {
            entity.setSettlementCompanyId(null);
            entity.setSettlementCompanyName(null);
            return;
        }
        entity.setSettlementCompanyId(carrier.getDefaultSettlementCompanyId());
        entity.setSettlementCompanyName(carrier.getDefaultSettlementCompanyName());
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

    private Specification<SalesOutbound> importableSalesOutboundStatus(String status) {
        return (root, query, cb) -> {
            String requestedStatus = BusinessDocumentValidator.trimToNull(status);
            if (requestedStatus != null && !StatusConstants.AUDITED.equals(requestedStatus)) {
                return cb.disjunction();
            }
            return cb.equal(root.get("status"), StatusConstants.AUDITED);
        };
    }

    private Specification<SalesOutbound> sourceOutboundNotOccupied() {
        return (root, query, cb) -> {
            var subquery = query.subquery(Long.class);
            var bill = subquery.from(FreightBill.class);
            Join<FreightBill, FreightBillItem> item = bill.join("items");
            subquery.select(cb.literal(1L)).where(
                    cb.isFalse(bill.get("deletedFlag")),
                    cb.equal(cb.trim(item.get("sourceNo")), root.get("outboundNo"))
            );
            return cb.not(cb.exists(subquery));
        };
    }

    private FreightBillImportCandidateResponse toImportCandidateResponse(SalesOutbound outbound) {
        return new FreightBillImportCandidateResponse(
                outbound.getId(),
                outbound.getOutboundNo(),
                outbound.getSalesOrderNo(),
                outbound.getCustomerName(),
                outbound.getProjectName(),
                outbound.getWarehouseName(),
                outbound.getSettlementCompanyId(),
                outbound.getSettlementCompanyName(),
                outbound.getOutboundDate(),
                outbound.getTotalWeight(),
                outbound.getTotalAmount(),
                outbound.getStatus()
        );
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
