package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.common.charge.api.DocumentChargeItemResponse;
import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.service.CrudVisibilityPolicy;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.mapper.FreightBillMapper;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.master.api.CarrierQuery;
import com.leo.erp.master.api.VehicleQuery;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class FreightBillService {

    private static final Logger log = LoggerFactory.getLogger(FreightBillService.class);
    private static final String[] SEARCH_FIELDS = {"billNo", "carrierCode", "carrierName"};
    private static final String MODULE_KEY = ModuleKeys.FREIGHT_BILL;
    private static final CrudStatusGuard<FreightBill> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();

    private final SnowflakeIdGenerator idGenerator;
    private final FreightBillRepository repository;
    private final FreightBillMapper mapper;
    private final FreightBillApplyService applyService;
    private final FreightBillCarrierResolver carrierResolver;
    private final CompanySettingService companySettingService;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final FreightBillDownstreamMutationGuard downstreamMutationGuard;
    private final VehicleQuery vehicleQuery;
    private final BusinessOperationEventPublisher businessOperationEventPublisher;
    private final DocumentChargeItemService documentChargeItemService;

    public FreightBillService(FreightBillRepository repository,
                              SnowflakeIdGenerator idGenerator,
                              FreightBillMapper mapper,
                              FreightBillApplyService applyService,
                              CarrierQuery carrierQuery,
                              CompanySettingService companySettingService,
                              SourceAllocationLockService sourceAllocationLockService,
                              FreightBillDownstreamMutationGuard downstreamMutationGuard,
                              VehicleQuery vehicleQuery,
                              BusinessOperationEventPublisher businessOperationEventPublisher,
                              DocumentChargeItemService documentChargeItemService) {
        this.idGenerator = idGenerator;
        this.repository = repository;
        this.mapper = mapper;
        this.applyService = applyService;
        this.carrierResolver = new FreightBillCarrierResolver(carrierQuery);
        this.companySettingService = companySettingService;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.downstreamMutationGuard = downstreamMutationGuard;
        this.vehicleQuery = vehicleQuery;
        this.businessOperationEventPublisher = businessOperationEventPublisher;
        this.documentChargeItemService = documentChargeItemService;
    }

    @Transactional(readOnly = true)
    public Page<FreightBillResponse> page(PageQuery query, PageFilter filter) {
        return page(query, filter, null);
    }

    @Transactional(readOnly = true)
    public Page<FreightBillResponse> page(PageQuery query, PageFilter filter, String carrierCode) {
        Specification<FreightBill> spec = Specs.<FreightBill>keywordLike(filter.keyword(), SEARCH_FIELDS)
                .and(Specs.equalValueIfPresent("carrierId", filter.carrierId()))
                .and(Specs.equalIfPresent("carrierCode", carrierCode))
                .and(Specs.equalIfPresent("carrierName", filter.name()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.betweenIfPresent("billTime", filter.startDate(), filter.endDate()));
        return pageEntities(query, spec).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FreightBillResponse detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    @Transactional
    public FreightBillResponse create(FreightBillRequest request) {
        FreightBillResponse created = createBill(
                request.audit() ? copyRequestWithStatus(request, StatusConstants.DRAFT) : request);
        documentChargeItemService.sync(MODULE_KEY, created.id(), request.chargeItems());
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Transactional
    public FreightBillResponse update(Long id, FreightBillRequest request) {
        FreightBillResponse updated = updateBill(id,
                request.audit() ? copyRequestWithStatus(request, StatusConstants.DRAFT) : request);
        documentChargeItemService.sync(MODULE_KEY, id, request.chargeItems());
        if (request.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
    }

    @Transactional
    public FreightBillResponse updateStatus(Long id, String status) {
        FreightBill bill = requireEntity(id);
        String currentStatus = bill.getStatus();
        FreightBillResponse response = doUpdateStatus(id, status);
        if (!Objects.equals(currentStatus, response.status())) {
            String actionType = StatusConstants.DRAFT.equals(response.status()) ? "反审核" : "审核";
            publishEvent(bill, "FREIGHT_BILL_STATUS_CHANGED", actionType,
                    "物流单状态 " + currentStatus + " -> " + response.status());
        }
        return response;
    }

    @Transactional
    public void delete(Long id) {
        FreightBill entity = requireEntity(id);
        STATUS_GUARD.assertDeleteAllowed(entity);
        beforeDelete(entity);
        entity.setDeletedFlag(true);
        saveEntity(entity);
        afterDelete(entity);
        log.info("{} deleted: id={}", entity.getClass().getSimpleName(), id);
    }

    /**
     * 基类 create 的显式内联：雪花 ID → 归一化 → 校验 → 应用 → 终态双写守卫 → 保存。
     */
    private FreightBillResponse createBill(FreightBillRequest request) {
        FreightBill entity = newEntity();
        long entityId = idGenerator.nextId();
        assignId(entity, entityId);
        FreightBillRequest normalized = normalizeCreateRequest(request, entityId);
        validateCreate(normalized);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        FreightBillResponse response = toSavedResponse(saveCreatedEntity(entity, normalized));
        log.info("{} created: id={}", entity.getClass().getSimpleName(), entityId);
        return response;
    }

    /**
     * 基类 update 的显式内联，状态断言序列逐字保持：
     * 编辑状态守卫 → 更新校验 → 快照当前状态 → 应用请求 →
     * assertRequestStatusTransitionAllowed → allowRequestToWriteFinalStatus 分支下的
     * assertRequestDidNotWriteFinalStatus → 保存。
     */
    private FreightBillResponse updateBill(Long id, FreightBillRequest request) {
        FreightBill entity = requireEntity(id);
        FreightBillRequest normalized = normalizeUpdateRequest(entity, request);
        STATUS_GUARD.assertEditAllowed(entity, allowProtectedStatusUpdate(entity, normalized));
        validateUpdate(entity, normalized);
        Optional<String> currentStatus = STATUS_GUARD.resolveStatus(entity);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestStatusTransitionAllowed(entity, currentStatus, allowedStatusTransitions());
        // 基类 allowRequestToWriteFinalStatus 默认 false：普通保存一律拒绝终态写入。
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        FreightBillResponse response = toSavedResponse(saveUpdatedEntity(entity, normalized));
        log.info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    /**
     * 基类 updateStatus 的显式内联：等值短路 → 迁移表校验 → beforeStatusUpdate → 写状态 → 状态保存。
     */
    private FreightBillResponse doUpdateStatus(Long id, String status) {
        FreightBill entity = requireEntity(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toSavedResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(allowedStatusTransitions(), currentStatus, nextStatus);
        beforeStatusUpdate(entity, currentStatus, nextStatus);
        STATUS_GUARD.writeStatus(entity, nextStatus);
        FreightBillResponse response = toSavedResponse(saveEntity(entity));
        log.info(
                "{} status updated: id={}, {} -> {}",
                entity.getClass().getSimpleName(),
                id,
                currentStatus,
                nextStatus
        );
        return response;
    }

    protected FreightBillResponse toDetailResponse(FreightBill entity) {
        FreightBillResponse response = mapper.toResponse(entity);
        List<DocumentChargeItemResponse> chargeItems =
                documentChargeItemService.list(MODULE_KEY, entity.getId());
        BigDecimal totalExpenseAmount = documentChargeItemService.sumAmount(chargeItems);
        return new FreightBillResponse(
                response.id(), response.billNo(), response.carrierId(), response.carrierCode(), response.carrierName(),
                response.settlementCompanyId(), response.settlementCompanyName(), response.vehicleId(),
                response.vehiclePlate(), response.billTime(),
                response.unitPrice(), response.totalWeight(), response.totalFreight(), response.status(),
                response.deletedFlag(), response.remark(), entity.getItems().stream().map(this::toItemResponse).toList(),
                totalExpenseAmount, chargeItems
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
                item.getWarehouseName(), null, null, item.getSourceSalesOrderItemId()
        );
    }

    protected void validateCreate(FreightBillRequest request) {
        if (repository.existsByBillNoAndDeletedFlagFalse(request.billNo())) {
            throw business("物流单号已存在");
        }
        String status = BusinessDocumentValidator.trimToNull(request.status());
        if (status != null && !StatusConstants.DRAFT.equals(status)) {
            throw business("新物流单只能保存为草稿");
        }
    }

    protected void validateUpdate(FreightBill entity, FreightBillRequest request) {
        if (!entity.getBillNo().equals(request.billNo())
                && repository.existsByBillNoAndDeletedFlagFalse(request.billNo())) {
            throw business("物流单号已存在");
        }
    }

    protected FreightBillRequest normalizeCreateRequest(FreightBillRequest request, long entityId) {
        return copyRequest(request, resolveCreateBusinessNo(entityId));
    }

    protected FreightBillRequest normalizeUpdateRequest(FreightBill entity, FreightBillRequest request) {
        assertOrdinaryUpdateKeepsStatus(entity.getStatus(), request.status());
        return copyRequest(request, entity.getBillNo());
    }

    private FreightBillRequest copyRequest(FreightBillRequest request, String billNo) {
        return new FreightBillRequest(
                billNo, request.carrierId(), request.carrierCode(), request.carrierName(),
                request.settlementCompanyId(), request.settlementCompanyName(), request.vehicleId(),
                request.vehiclePlate(), request.billTime(),
                request.unitPrice(), request.status(), request.remark(), request.items(), request.chargeItems(), request.audit()
        );
    }

    private FreightBillRequest copyRequestWithStatus(FreightBillRequest request, String status) {
        return new FreightBillRequest(
                request.billNo(), request.carrierId(), request.carrierCode(), request.carrierName(),
                request.settlementCompanyId(), request.settlementCompanyName(), request.vehicleId(),
                request.vehiclePlate(), request.billTime(),
                request.unitPrice(), status, request.remark(), request.items(), request.chargeItems(), request.audit()
        );
    }

    private void assertOrdinaryUpdateKeepsStatus(String currentStatus, String requestedStatus) {
        String normalized = BusinessDocumentValidator.trimToNull(requestedStatus);
        if (normalized != null && !Objects.equals(currentStatus, normalized)) {
            throw business("物流单状态只能通过审核或反审核操作变更");
        }
    }

    protected Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.FREIGHT_BILL_AUDIT_TRANSITIONS;
    }

    private boolean allowProtectedStatusUpdate(FreightBill entity, FreightBillRequest request) {
        // 基类 allowProtectedStatusUpdate 默认 false：受保护状态单据不允许普通编辑。
        return false;
    }

    protected void apply(FreightBill entity, FreightBillRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(), StatusConstants.DRAFT, "物流单状态", StatusConstants.ALLOWED_FREIGHT_BILL_STATUS
        );
        FreightBillCarrierResolver.CarrierSnapshot carrier = carrierResolver.resolve(
                request.carrierId(), request.carrierCode(), request.carrierName()
        );
        if (entity.getCarrierId() != null && !Objects.equals(entity.getCarrierId(), carrier.id())) {
            throw business("物流单保存后不能更换物流商");
        }
        VehicleSnapshot vehicle = resolveVehicle(request, carrier);
        entity.setBillNo(request.billNo());
        entity.setCarrierId(carrier.id());
        entity.setCarrierCode(carrier.code());
        entity.setCarrierName(carrier.name());
        applySettlementCompany(entity, request, carrier);
        entity.setVehicleId(vehicle.id());
        entity.setVehiclePlate(vehicle.plate());
        entity.setCustomerName(null);
        entity.setProjectName(null);
        entity.setBillTime(request.billTime());
        entity.setUnitPrice(request.unitPrice());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());
        applyService.applyItems(entity, request, this::nextId);
    }

    protected void beforeStatusUpdate(FreightBill entity, String currentStatus, String nextStatus) {
        lockCurrent(entity);
        if (StatusConstants.AUDITED.equals(currentStatus) && StatusConstants.DRAFT.equals(nextStatus)) {
            downstreamMutationGuard.assertReverseAuditAllowed(entity);
        }
    }

    protected void beforeDelete(FreightBill entity) {
        lockCurrent(entity);
        downstreamMutationGuard.assertDeleteAllowed(entity);
    }

    protected void afterDelete(FreightBill entity) {
        documentChargeItemService.removeAll(MODULE_KEY, entity.getId());
        entity.getSourceOrders().forEach(source -> source.setActiveFlag(false));
        publishEvent(entity, "FREIGHT_BILL_DELETED", "删除", "删除物流单 " + entity.getBillNo());
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
        VehicleQuery.VehicleSnapshot vehicle;
        if (requestedId != null) {
            vehicle = vehicleQuery.findById(requestedId).orElseThrow(() -> business("车辆不存在"));
        } else {
            List<VehicleQuery.VehicleSnapshot> candidates = vehicleQuery
                    .findByCarrierIdOrderBySortOrder(carrier.id()).stream()
                    .filter(candidate -> Objects.equals(requestedPlate,
                            BusinessDocumentValidator.trimToNull(candidate.plate())))
                    .toList();
            if (candidates.isEmpty()) {
                return new VehicleSnapshot(null, VehiclePlateValidator.normalizeAndValidate(requestedPlate));
            }
            if (candidates.size() != 1) {
                throw business("车牌号对应多辆车，请选择车辆ID");
            }
            vehicle = candidates.get(0);
            log.warn("identity_fallback module=freight-bill field=vehicleId reason=vehicle-plate resolvedId={}",
                    vehicle.id());
        }
        Long vehicleCarrierId = vehicle.carrierId();
        if (carrier.id() != null && !Objects.equals(carrier.id(), vehicleCarrierId)) {
            throw business("车辆不属于所选物流商");
        }
        String plate = BusinessDocumentValidator.trimToNull(vehicle.plate());
        if (requestedPlate != null && !Objects.equals(requestedPlate, plate)) {
            throw business("车辆ID与车牌号不一致");
        }
        return new VehicleSnapshot(vehicle.id(), plate);
    }

    private void applySettlementCompany(FreightBill entity,
                                        FreightBillRequest request,
                                        FreightBillCarrierResolver.CarrierSnapshot carrier) {
        if (carrier.defaultSettlementCompanyId() == null) {
            throw business("物流商未配置默认结算主体，请先完善物流商档案");
        }
        CompanySetting company = companySettingService.requireActiveSettlementCompany(carrier.defaultSettlementCompanyId());
        entity.setSettlementCompanyId(company.getId());
        entity.setSettlementCompanyName(company.getCompanyName());
    }

    private String resolveMaterialName(FreightBillItem item) {
        String name = BusinessDocumentValidator.trimToNull(item.getMaterialName());
        return name != null ? name : BusinessDocumentValidator.trimToNull(item.getBrand());
    }

    protected FreightBill newEntity() {
        return new FreightBill();
    }

    protected void assignId(FreightBill entity, Long id) {
        entity.setId(id);
    }

    protected Optional<FreightBill> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    protected Optional<FreightBill> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    protected String notFoundMessage() {
        return "物流单不存在";
    }

    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    protected FreightBill saveEntity(FreightBill entity) {
        return repository.save(entity);
    }

    protected FreightBill saveCreatedEntity(FreightBill entity, FreightBillRequest request) {
        FreightBill saved = saveEntity(entity);
        publishEvent(saved, "FREIGHT_BILL_CREATED", "新增", "新增物流单 " + saved.getBillNo());
        return saved;
    }

    protected FreightBill saveUpdatedEntity(FreightBill entity, FreightBillRequest request) {
        lockCurrent(entity);
        FreightBill saved = saveEntity(entity);
        publishEvent(saved, "FREIGHT_BILL_UPDATED", "编辑", "编辑物流单 " + saved.getBillNo());
        return saved;
    }

    private void publishEvent(FreightBill bill, String eventType, String actionType, String remark) {
        businessOperationEventPublisher.publish(
                eventType,
                "freight-bill",
                "物流单",
                actionType,
                "FreightBill",
                bill.getId(),
                bill.getBillNo(),
                remark
        );
    }

    protected FreightBillResponse toResponse(FreightBill entity) {
        return mapper.toResponse(entity);
    }

    protected FreightBillResponse toSavedResponse(FreightBill entity) {
        return toDetailResponse(entity);
    }

    private FreightBill requireEntity(Long id) {
        return findActiveEntity(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    private FreightBill requireDetailEntity(Long id) {
        if (allowViewingDeletedRecords()) {
            return findVisibleEntity(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        }
        return requireEntity(id);
    }

    private Page<FreightBill> pageEntities(PageQuery query, Specification<FreightBill> specification) {
        Specification<FreightBill> effectiveSpec =
                VISIBILITY_POLICY.applyDeletedVisibility(specification, allowViewingDeletedRecords());
        return repository.findAll(effectiveSpec, query.toPageable("id"));
    }

    private long nextId() {
        return idGenerator.nextId();
    }

    private String resolveCreateBusinessNo(Long entityId) {
        if (entityId == null || entityId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "业务单据雪花ID尚未分配");
        }
        return String.valueOf(entityId);
    }

    private BusinessException business(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }

    private record VehicleSnapshot(Long id, String plate) {
        private static final VehicleSnapshot EMPTY = new VehicleSnapshot(null, null);
    }
}
