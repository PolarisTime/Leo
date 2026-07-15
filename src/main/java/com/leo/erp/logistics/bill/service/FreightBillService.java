package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
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
import com.leo.erp.logistics.bill.web.dto.FreightBillItemResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.master.carrier.domain.entity.Vehicle;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.repository.VehicleRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class FreightBillService extends AbstractCrudService<FreightBill, FreightBillRequest, FreightBillResponse> {

    private static final Logger log = LoggerFactory.getLogger(FreightBillService.class);
    private static final String[] SEARCH_FIELDS = {"billNo", "carrierCode", "carrierName", "customerName"};

    private final FreightBillRepository repository;
    private final FreightBillMapper mapper;
    private final FreightBillApplyService applyService;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final FreightBillCarrierResolver carrierResolver;
    private final CompanySettingService companySettingService;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final FreightBillDownstreamMutationGuard downstreamMutationGuard;
    private final VehicleRepository vehicleRepository;

    public FreightBillService(FreightBillRepository repository,
                              SnowflakeIdGenerator idGenerator,
                              FreightBillMapper mapper,
                              FreightBillApplyService applyService,
                              WorkflowTransitionGuard workflowTransitionGuard,
                              CarrierRepository carrierRepository,
                              CompanySettingService companySettingService,
                              SourceAllocationLockService sourceAllocationLockService,
                              FreightBillDownstreamMutationGuard downstreamMutationGuard,
                              VehicleRepository vehicleRepository) {
        super(idGenerator);
        this.repository = repository;
        this.mapper = mapper;
        this.applyService = applyService;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.carrierResolver = new FreightBillCarrierResolver(carrierRepository);
        this.companySettingService = companySettingService;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.downstreamMutationGuard = downstreamMutationGuard;
        this.vehicleRepository = vehicleRepository;
    }

    public Page<FreightBillResponse> page(PageQuery query, PageFilter filter) {
        return page(query, filter, null);
    }

    public Page<FreightBillResponse> page(PageQuery query, PageFilter filter, String carrierCode) {
        Specification<FreightBill> spec = Specs.<FreightBill>keywordLike(filter.keyword(), SEARCH_FIELDS)
                .and(Specs.equalValueIfPresent("carrierId", filter.carrierId()))
                .and(Specs.equalIfPresent("carrierCode", carrierCode))
                .and(Specs.equalIfPresent("carrierName", filter.name()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("billTime", filter.startDate(), filter.endDate()));
        return super.page(query, spec, repository);
    }

    @Transactional(readOnly = true)
    public List<FreightBillResponse> search(String keyword, int maxSize) {
        return super.search(keyword, SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected FreightBillResponse toDetailResponse(FreightBill entity) {
        FreightBillResponse response = mapper.toResponse(entity);
        return new FreightBillResponse(
                response.id(), response.billNo(), response.carrierId(), response.carrierCode(), response.carrierName(),
                response.settlementCompanyId(), response.settlementCompanyName(), response.vehicleId(),
                response.vehiclePlate(), response.customerName(), response.projectName(), response.billTime(),
                response.unitPrice(), response.totalWeight(), response.totalFreight(), response.status(),
                response.deletedFlag(), response.remark(), entity.getItems().stream().map(this::toItemResponse).toList()
        );
    }

    private FreightBillItemResponse toItemResponse(FreightBillItem item) {
        return new FreightBillItemResponse(
                item.getId(), item.getLineNo(), item.getSourceNo(), item.getSettlementCompanyId(),
                item.getSettlementCompanyName(), item.getCustomerId(), item.getCustomerName(), item.getProjectId(),
                item.getProjectName(), item.getMaterialId(), item.getMaterialCode(), resolveMaterialName(item),
                item.getBrand(), item.getCategory(), item.getMaterial(), item.getSpec(), item.getLength(),
                item.getQuantity(), item.getQuantityUnit(), item.getPieceWeightTon(), item.getPiecesPerBundle(),
                item.getBatchNo(), item.getBatchNoNormalized(), item.getWeightTon(), item.getWarehouseId(),
                item.getWarehouseName(), null, null
        );
    }

    @Override
    protected void validateCreate(FreightBillRequest request) {
        if (repository.existsByBillNoAndDeletedFlagFalse(request.billNo())) {
            throw business("物流单号已存在");
        }
        String status = BusinessDocumentValidator.trimToNull(request.status());
        if (status != null && !StatusConstants.UNAUDITED.equals(status)) {
            throw business("新物流单只能保存为未审核，审核必须通过状态操作完成");
        }
    }

    @Override
    protected void validateUpdate(FreightBill entity, FreightBillRequest request) {
        if (!entity.getBillNo().equals(request.billNo())
                && repository.existsByBillNoAndDeletedFlagFalse(request.billNo())) {
            throw business("物流单号已存在");
        }
    }

    @Override
    protected FreightBillRequest normalizeCreateRequest(FreightBillRequest request, long entityId) {
        return copyRequest(request, resolveCreateBusinessNo("freight-bill", request.billNo(), entityId));
    }

    @Override
    protected FreightBillRequest normalizeUpdateRequest(FreightBill entity, FreightBillRequest request) {
        assertOrdinaryUpdateKeepsStatus(entity.getStatus(), request.status());
        return copyRequest(request, entity.getBillNo());
    }

    private FreightBillRequest copyRequest(FreightBillRequest request, String billNo) {
        return new FreightBillRequest(
                billNo, request.carrierId(), request.carrierCode(), request.carrierName(),
                request.settlementCompanyId(), request.settlementCompanyName(), request.vehicleId(),
                request.vehiclePlate(), request.customerName(), request.projectName(), request.billTime(),
                request.unitPrice(), request.status(), request.remark(), request.items()
        );
    }

    private void assertOrdinaryUpdateKeepsStatus(String currentStatus, String requestedStatus) {
        String normalized = BusinessDocumentValidator.trimToNull(requestedStatus);
        if (normalized != null && !Objects.equals(currentStatus, normalized)) {
            throw business("物流单状态只能通过审核或反审核操作变更");
        }
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return StatusConstants.FREIGHT_BILL_AUDIT_TRANSITIONS;
    }

    @Override
    protected void apply(FreightBill entity, FreightBillRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(), StatusConstants.UNAUDITED, "物流单状态", StatusConstants.ALLOWED_FREIGHT_BILL_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "freight-bill", entity.getStatus(), nextStatus, StatusConstants.AUDITED
        );
        FreightBillCarrierResolver.CarrierSnapshot carrier = carrierResolver.resolve(
                request.carrierId(), request.carrierCode(), request.carrierName()
        );
        VehicleSnapshot vehicle = resolveVehicle(request, carrier);
        entity.setBillNo(request.billNo());
        entity.setCarrierId(carrier.id());
        entity.setCarrierCode(carrier.code());
        entity.setCarrierName(carrier.name());
        applySettlementCompany(entity, request, carrier);
        entity.setVehicleId(vehicle.id());
        entity.setVehiclePlate(vehicle.plate());
        entity.setCustomerName(request.customerName().trim());
        entity.setProjectName(request.projectName().trim());
        entity.setBillTime(request.billTime());
        entity.setUnitPrice(request.unitPrice());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());
        applyService.applyItems(entity, request, this::nextId);
    }

    @Override
    protected void beforeStatusUpdate(FreightBill entity, String currentStatus, String nextStatus) {
        lockCurrent(entity);
        if (StatusConstants.AUDITED.equals(currentStatus) && StatusConstants.UNAUDITED.equals(nextStatus)) {
            downstreamMutationGuard.assertReverseAuditAllowed(entity);
        }
    }

    @Override
    protected void beforeDelete(FreightBill entity) {
        lockCurrent(entity);
        downstreamMutationGuard.assertDeleteAllowed(entity);
    }

    private void lockCurrent(FreightBill entity) {
        if (entity.getId() != null) {
            sourceAllocationLockService.lockDocumentSources(List.of(), List.of(), List.of(), List.of(entity.getId()));
        }
    }

    private VehicleSnapshot resolveVehicle(FreightBillRequest request,
                                           FreightBillCarrierResolver.CarrierSnapshot carrier) {
        Long requestedId = request.vehicleId();
        String requestedPlate = BusinessDocumentValidator.trimToNull(request.vehiclePlate());
        if (requestedId == null && requestedPlate == null) {
            return VehicleSnapshot.EMPTY;
        }
        Vehicle vehicle;
        if (requestedId != null) {
            vehicle = vehicleRepository.findById(requestedId).orElseThrow(() -> business("车辆不存在"));
        } else {
            List<Vehicle> candidates = vehicleRepository.findByCarrierIdOrderBySortOrderAsc(carrier.id()).stream()
                    .filter(candidate -> Objects.equals(requestedPlate,
                            BusinessDocumentValidator.trimToNull(candidate.getPlate())))
                    .toList();
            if (candidates.size() != 1) {
                throw business(candidates.isEmpty() ? "车辆不存在" : "车牌号对应多辆车，请选择车辆ID");
            }
            vehicle = candidates.get(0);
            log.warn("identity_fallback module=freight-bill field=vehicleId reason=vehicle-plate resolvedId={}",
                    vehicle.getId());
        }
        Long vehicleCarrierId = vehicle.getCarrier() == null ? null : vehicle.getCarrier().getId();
        if (carrier.id() != null && !Objects.equals(carrier.id(), vehicleCarrierId)) {
            throw business("车辆不属于所选物流商");
        }
        String plate = BusinessDocumentValidator.trimToNull(vehicle.getPlate());
        if (requestedPlate != null && !Objects.equals(requestedPlate, plate)) {
            throw business("车辆ID与车牌号不一致");
        }
        return new VehicleSnapshot(vehicle.getId(), plate);
    }

    private void applySettlementCompany(FreightBill entity,
                                        FreightBillRequest request,
                                        FreightBillCarrierResolver.CarrierSnapshot carrier) {
        if (request.settlementCompanyId() == null) {
            entity.setSettlementCompanyId(carrier.defaultSettlementCompanyId());
            entity.setSettlementCompanyName(carrier.defaultSettlementCompanyName());
            return;
        }
        CompanySetting company = companySettingService.requireActiveSettlementCompany(request.settlementCompanyId());
        entity.setSettlementCompanyId(company.getId());
        entity.setSettlementCompanyName(company.getCompanyName());
    }

    private String resolveMaterialName(FreightBillItem item) {
        String name = BusinessDocumentValidator.trimToNull(item.getMaterialName());
        return name != null ? name : BusinessDocumentValidator.trimToNull(item.getBrand());
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
    protected FreightBill saveEntity(FreightBill entity) {
        return repository.save(entity);
    }

    @Override
    protected FreightBill saveUpdatedEntity(FreightBill entity, FreightBillRequest request) {
        lockCurrent(entity);
        return saveEntity(entity);
    }

    @Override
    protected FreightBillResponse toResponse(FreightBill entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected FreightBillResponse toSavedResponse(FreightBill entity) {
        return toDetailResponse(entity);
    }

    private BusinessException business(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }

    private record VehicleSnapshot(Long id, String plate) {
        private static final VehicleSnapshot EMPTY = new VehicleSnapshot(null, null);
    }
}
