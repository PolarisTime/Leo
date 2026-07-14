package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
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
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.domain.entity.Vehicle;
import com.leo.erp.master.carrier.repository.VehicleRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.logistics.bill.web.dto.*;
import jakarta.persistence.criteria.Join;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class FreightBillService extends AbstractCrudService<FreightBill, FreightBillRequest, FreightBillResponse> {

    private static final Logger log = LoggerFactory.getLogger(FreightBillService.class);

    private static final Set<String> IMPORTABLE_OUTBOUND_STATUSES =
            Set.of(StatusConstants.PRE_OUTBOUND, StatusConstants.AUDITED);

    private final FreightBillRepository repository;
    private final SalesOutboundRepository salesOutboundRepository;
    private final FreightBillMapper freightBillMapper;
    private final FreightBillSourceService freightBillSourceService;
    private final FreightBillApplyService freightBillApplyService;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final FreightBillCarrierResolver carrierResolver;
    private final CompanySettingService companySettingService;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final FreightBillDownstreamMutationGuard downstreamMutationGuard;
    private final VehicleRepository vehicleRepository;
    private FreightBillSalesOrderSourceService salesOrderSourceService;
    private FreightBillSalesOrderAuditService salesOrderAuditService;

    public FreightBillService(FreightBillRepository repository,
                              SalesOutboundRepository salesOutboundRepository,
                              SnowflakeIdGenerator idGenerator,
                              FreightBillMapper freightBillMapper,
                              FreightBillSourceService freightBillSourceService,
                              FreightBillApplyService freightBillApplyService,
                              WorkflowTransitionGuard workflowTransitionGuard,
                              SourceAllocationLockService sourceAllocationLockService) {
        this(repository, salesOutboundRepository, idGenerator, freightBillMapper, freightBillSourceService,
                freightBillApplyService, workflowTransitionGuard, null, null, sourceAllocationLockService, null);
    }

    public FreightBillService(FreightBillRepository repository,
                              SalesOutboundRepository salesOutboundRepository,
                              SnowflakeIdGenerator idGenerator,
                              FreightBillMapper freightBillMapper,
                              FreightBillSourceService freightBillSourceService,
                              FreightBillApplyService freightBillApplyService,
                              WorkflowTransitionGuard workflowTransitionGuard,
                              CarrierRepository carrierRepository,
                              SourceAllocationLockService sourceAllocationLockService) {
        this(repository, salesOutboundRepository, idGenerator, freightBillMapper, freightBillSourceService,
                freightBillApplyService, workflowTransitionGuard, carrierRepository, null, sourceAllocationLockService, null);
    }

    public FreightBillService(FreightBillRepository repository,
                              SalesOutboundRepository salesOutboundRepository,
                              SnowflakeIdGenerator idGenerator,
                              FreightBillMapper freightBillMapper,
                              FreightBillSourceService freightBillSourceService,
                              FreightBillApplyService freightBillApplyService,
                              WorkflowTransitionGuard workflowTransitionGuard,
                              CarrierRepository carrierRepository,
                              CompanySettingService companySettingService,
                              SourceAllocationLockService sourceAllocationLockService) {
        this(repository, salesOutboundRepository, idGenerator, freightBillMapper, freightBillSourceService,
                freightBillApplyService, workflowTransitionGuard, carrierRepository, companySettingService,
                sourceAllocationLockService, null);
    }

    public FreightBillService(FreightBillRepository repository,
                              SalesOutboundRepository salesOutboundRepository,
                              SnowflakeIdGenerator idGenerator,
                              FreightBillMapper freightBillMapper,
                              FreightBillSourceService freightBillSourceService,
                              FreightBillApplyService freightBillApplyService,
                              WorkflowTransitionGuard workflowTransitionGuard,
                              CarrierRepository carrierRepository,
                              CompanySettingService companySettingService,
                              SourceAllocationLockService sourceAllocationLockService,
                              FreightBillDownstreamMutationGuard downstreamMutationGuard) {
        this(repository, salesOutboundRepository, idGenerator, freightBillMapper, freightBillSourceService,
                freightBillApplyService, workflowTransitionGuard, carrierRepository, companySettingService,
                sourceAllocationLockService, downstreamMutationGuard, null);
    }

    @Autowired
    public FreightBillService(FreightBillRepository repository,
                              SalesOutboundRepository salesOutboundRepository,
                              SnowflakeIdGenerator idGenerator,
                              FreightBillMapper freightBillMapper,
                              FreightBillSourceService freightBillSourceService,
                              FreightBillApplyService freightBillApplyService,
                              WorkflowTransitionGuard workflowTransitionGuard,
                              CarrierRepository carrierRepository,
                              CompanySettingService companySettingService,
                              SourceAllocationLockService sourceAllocationLockService,
                              FreightBillDownstreamMutationGuard downstreamMutationGuard,
                              VehicleRepository vehicleRepository) {
        super(idGenerator);
        this.repository = repository;
        this.salesOutboundRepository = salesOutboundRepository;
        this.freightBillMapper = freightBillMapper;
        this.freightBillSourceService = freightBillSourceService;
        this.freightBillApplyService = freightBillApplyService;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.carrierResolver = carrierRepository == null ? null : new FreightBillCarrierResolver(carrierRepository);
        this.companySettingService = companySettingService;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.downstreamMutationGuard = downstreamMutationGuard;
        this.vehicleRepository = vehicleRepository;
    }

    @Autowired
    void setSalesOrderFlowServices(FreightBillSalesOrderSourceService salesOrderSourceService,
                                   FreightBillSalesOrderAuditService salesOrderAuditService) {
        this.salesOrderSourceService = salesOrderSourceService;
        this.salesOrderAuditService = salesOrderAuditService;
    }

    public Page<FreightBillResponse> page(PageQuery query, PageFilter filter) {
        return page(query, filter, null);
    }

    public Page<FreightBillResponse> page(PageQuery query, PageFilter filter, String carrierCode) {
        Specification<FreightBill> spec = Specs.<FreightBill>keywordLike(
                        filter.keyword(),
                        "billNo",
                        "carrierCode",
                        "carrierName",
                        "customerName"
                )
                .and(Specs.equalValueIfPresent("carrierId", filter.carrierId()))
                .and(Specs.equalIfPresent("carrierCode", carrierCode))
                .and(Specs.equalIfPresent("carrierName", filter.name()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("billTime", filter.startDate(), filter.endDate()));
        Page<FreightBillResponse> responses = super.page(query, spec, repository);
        Map<Long, SalesOutboundRepository.FreightBillOutboundReference> outboundReferences =
                resolveOutboundReferences(responses.getContent());
        return responses.map(response -> attachOutboundReference(response, outboundReferences.get(response.id())));
    }

    @Transactional(readOnly = true)
    public Page<FreightBillImportCandidateResponse> importCandidates(PageQuery query, PageFilter filter) {
        Specification<SalesOutbound> spec = Specs.<SalesOutbound>notDeleted()
                .and(Specs.keywordLike(filter.keyword(), "outboundNo", "salesOrderNo", "customerName", "projectName"))
                .and(Specs.equalValueIfPresent("customerId", filter.customerId()))
                .and(Specs.equalValueIfPresent("projectId", filter.projectId()))
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(importableSalesOutboundStatus(filter.status()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.betweenIfPresent("outboundDate", filter.startDate(), filter.endDate()))
                .and(sourceOutboundNotOccupied(filter.currentRecordId()));
        return salesOutboundRepository.findAll(DataScopeContext.apply(spec), query.toPageable("id"))
                .map(this::toImportCandidateResponse);
    }

    private static final String[] FREIGHT_BILL_SEARCH_FIELDS = {
            "billNo",
            "carrierCode",
            "carrierName",
            "customerName"
    };

    public java.util.List<FreightBillResponse> search(String keyword, int maxSize) {
        List<FreightBillResponse> responses = super.search(
                keyword,
                FREIGHT_BILL_SEARCH_FIELDS,
                maxSize,
                null,
                repository
        );
        Map<Long, SalesOutboundRepository.FreightBillOutboundReference> outboundReferences =
                resolveOutboundReferences(responses);
        return responses.stream()
                .map(response -> attachOutboundReference(response, outboundReferences.get(response.id())))
                .toList();
    }

    @Override
    protected FreightBillResponse toDetailResponse(FreightBill entity) {
        FreightBillResponse response = freightBillMapper.toResponse(entity);
        Map<Long, String> sourceStatusByItemId = resolveSourceOutboundStatusByItemId(entity.getItems());
        SalesOutboundRepository.FreightBillOutboundReference outboundReference = entity.getId() == null
                ? null
                : resolveOutboundReferences(List.of(response)).get(entity.getId());
        return new FreightBillResponse(
                response.id(), response.billNo(),
                entity.getCarrierId(), response.carrierCode(), response.carrierName(),
                response.settlementCompanyId(), response.settlementCompanyName(), entity.getVehicleId(),
                response.vehiclePlate(),
                response.customerName(), response.projectName(),
                response.billTime(), response.unitPrice(), response.totalWeight(),
                response.totalFreight(), response.status(),
                response.deletedFlag(),
                response.remark(),
                entity.getItems().stream().map(item -> new FreightBillItemResponse(
                        item.getId(), item.getLineNo(), item.getSourceNo(),
                        item.getSourceSalesOutboundItemId(),
                        resolveSourceStatus(item, sourceStatusByItemId),
                        item.getSettlementCompanyId(), item.getSettlementCompanyName(),
                        item.getCustomerId(), item.getCustomerName(), item.getProjectId(), item.getProjectName(),
                        item.getMaterialId(), item.getMaterialCode(),
                        resolveMaterialName(item), item.getBrand(), item.getCategory(),
                        item.getMaterial(), item.getSpec(), item.getLength(),
                        item.getQuantity(), item.getQuantityUnit(), item.getPieceWeightTon(), item.getPiecesPerBundle(),
                        item.getBatchNo(), item.getBatchNoNormalized(), item.getWeightTon(), item.getWarehouseId(),
                        item.getWarehouseName(), null, null, item.getSourceSalesOrderItemId(), null
                )).toList(),
                entity.getSourceSalesOrderId(),
                outboundReference == null ? null : outboundReference.getOutboundId(),
                outboundReference == null ? null : outboundReference.getOutboundNo()
        );
    }

    private Map<Long, SalesOutboundRepository.FreightBillOutboundReference> resolveOutboundReferences(
            List<FreightBillResponse> responses
    ) {
        List<Long> freightBillIds = responses.stream()
                .map(FreightBillResponse::id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (freightBillIds.isEmpty()) {
            return Map.of();
        }
        return salesOutboundRepository.findActiveFreightBillOutboundReferences(freightBillIds).stream()
                .collect(Collectors.toMap(
                        SalesOutboundRepository.FreightBillOutboundReference::getFreightBillId,
                        reference -> reference,
                        (left, ignored) -> left
                ));
    }

    private FreightBillResponse attachOutboundReference(
            FreightBillResponse response,
            SalesOutboundRepository.FreightBillOutboundReference reference
    ) {
        return new FreightBillResponse(
                response.id(), response.billNo(), response.carrierId(), response.carrierCode(), response.carrierName(),
                response.settlementCompanyId(), response.settlementCompanyName(), response.vehicleId(),
                response.vehiclePlate(), response.customerName(), response.projectName(), response.billTime(),
                response.unitPrice(), response.totalWeight(), response.totalFreight(), response.status(),
                response.deletedFlag(), response.remark(), response.items(), response.sourceSalesOrderId(),
                reference == null ? null : reference.getOutboundId(),
                reference == null ? null : reference.getOutboundNo()
        );
    }

    private String resolveSourceStatus(FreightBillItem item, Map<Long, String> sourceStatusByItemId) {
        Long sourceItemId = item.getSourceSalesOutboundItemId();
        return sourceItemId == null ? null : sourceStatusByItemId.get(sourceItemId);
    }

    private Map<Long, String> resolveSourceOutboundStatusByItemId(List<FreightBillItem> items) {
        Set<Long> sourceItemIds = items.stream()
                .map(FreightBillItem::getSourceSalesOutboundItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }
        return salesOutboundRepository.findSourceOutboundStatusesByItemIds(sourceItemIds).stream()
                .collect(Collectors.toMap(
                        SalesOutboundRepository.SourceOutboundStatusProjection::getItemId,
                        SalesOutboundRepository.SourceOutboundStatusProjection::getStatus,
                        (left, ignored) -> left
                ));
    }

    @Override
    protected void validateCreate(FreightBillRequest request) {
        if (repository.existsByBillNoAndDeletedFlagFalse(request.billNo())) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "物流单号已存在");
        }
        String requestedStatus = BusinessDocumentValidator.trimToNull(request.status());
        if (requestedStatus != null && !StatusConstants.UNAUDITED.equals(requestedStatus)) {
            throw new BusinessException(
                    com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "新物流单只能保存为未审核，审核必须通过状态操作完成"
            );
        }
        if (request.sourceSalesOrderId() == null
                || request.items().stream().anyMatch(item -> item.sourceSalesOrderItemId() == null)) {
            throw new BusinessException(
                    com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "新物流单必须从已审核销售订单完整导入"
            );
        }
    }

    @Override
    protected void validateUpdate(FreightBill entity, FreightBillRequest request) {
        if (entity.getSourceSalesOrderId() == null) {
            throw new BusinessException(
                    com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "历史销售出库来源物流单仅允许查看"
            );
        }
        assertSalesOutboundNotGenerated(entity);
        if (!entity.getBillNo().equals(request.billNo()) && repository.existsByBillNoAndDeletedFlagFalse(request.billNo())) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "物流单号已存在");
        }
    }

    @Override
    protected FreightBillRequest normalizeCreateRequest(FreightBillRequest request, long entityId) {
        return new FreightBillRequest(
                resolveCreateBusinessNo("freight-bill", request.billNo(), entityId),
                request.carrierId(),
                request.carrierCode(),
                request.carrierName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.vehicleId(),
                request.vehiclePlate(),
                request.customerName(),
                request.projectName(),
                request.billTime(),
                request.unitPrice(),
                request.status(),
                request.remark(),
                request.items(),
                request.sourceSalesOrderId()
        );
    }

    @Override
    protected FreightBillRequest normalizeUpdateRequest(FreightBill entity, FreightBillRequest request) {
        assertOrdinaryUpdateKeepsStatus(entity.getStatus(), request.status());
        return new FreightBillRequest(
                entity.getBillNo(),
                request.carrierId(),
                request.carrierCode(),
                request.carrierName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.vehicleId(),
                request.vehiclePlate(),
                request.customerName(),
                request.projectName(),
                request.billTime(),
                request.unitPrice(),
                entity.getStatus(),
                request.remark(),
                request.items(),
                entity.getSourceSalesOrderId() == null
                        ? request.sourceSalesOrderId()
                        : entity.getSourceSalesOrderId()
        );
    }

    private void assertOrdinaryUpdateKeepsStatus(String currentStatus, String requestedStatus) {
        String normalizedRequestedStatus = BusinessDocumentValidator.trimToNull(requestedStatus);
        if (normalizedRequestedStatus != null && !Objects.equals(currentStatus, normalizedRequestedStatus)) {
            throw new BusinessException(
                    com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "物流单状态只能通过审核或反审核操作变更"
            );
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
        lockSources(entity, request);
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
        FreightBillCarrierResolver.CarrierSnapshot carrier = resolveCarrier(request);
        VehicleSnapshot vehicle = resolveVehicle(request, carrier);
        entity.setBillNo(request.billNo());
        entity.setCarrierId(carrier.id());
        entity.setCarrierCode(carrier.code());
        entity.setCarrierName(carrier.name());
        applySettlementCompany(entity, request, carrier);
        entity.setVehicleId(vehicle.id());
        entity.setVehiclePlate(vehicle.plate());
        entity.setBillTime(request.billTime());
        entity.setUnitPrice(request.unitPrice());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        if (usesSalesOrderSource(entity, request)) {
            FreightBillSalesOrderSourceService.SourceContext sourceContext =
                    salesOrderSourceService.validate(request, entity.getId());
            entity.setSourceSalesOrderId(sourceContext.order().getId());
            freightBillApplyService.applySalesOrderItems(entity, request, sourceContext, this::nextId);
        } else {
            FreightBillSourceService.SourceValidationContext sourceContext =
                    freightBillSourceService.validateSources(request, entity.getId());
            if (StatusConstants.AUDITED.equals(nextStatus)) {
                freightBillSourceService.assertSourcesAuditable(sourceContext);
            }
            freightBillApplyService.applyItems(entity, request, sourceContext, this::nextId);
        }
    }

    @Override
    protected void beforeStatusUpdate(FreightBill entity, String currentStatus, String nextStatus) {
        lockSources(entity, null);
        if (StatusConstants.AUDITED.equals(currentStatus) && StatusConstants.UNAUDITED.equals(nextStatus)) {
            lockCurrentFreightBill(entity);
            if (downstreamMutationGuard != null) {
                downstreamMutationGuard.assertReverseAuditAllowed(entity);
            }
        }
        if (!StatusConstants.AUDITED.equals(nextStatus)) {
            return;
        }
        if (entity.getSourceSalesOrderId() != null) {
            salesOrderAuditService.synchronizeActualWeightAndAssertAuditable(entity);
        } else {
            freightBillSourceService.assertSourceItemsAuditable(
                    entity.getItems().stream()
                            .map(FreightBillItem::getSourceSalesOutboundItemId)
                            .toList()
            );
        }
    }

    @Override
    protected void beforeDelete(FreightBill entity) {
        lockSources(entity, null);
        lockCurrentFreightBill(entity);
        if (downstreamMutationGuard != null) {
            downstreamMutationGuard.assertDeleteAllowed(entity);
        }
        if (salesOutboundRepository.existsBySourceFreightBillIdAndDeletedFlagFalse(entity.getId())) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "物流单已生成销售出库，不能删除");
        }
    }

    private void lockCurrentFreightBill(FreightBill entity) {
        if (entity.getId() == null) {
            return;
        }
        sourceAllocationLockService.lockDocumentSources(
                List.of(),
                List.of(),
                List.of(),
                List.of(entity.getId())
        );
    }

    private void assertSalesOutboundNotGenerated(FreightBill entity) {
        if (entity.getId() != null
                && salesOutboundRepository.existsBySourceFreightBillIdAndDeletedFlagFalse(entity.getId())) {
            throw new BusinessException(
                    com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "物流单已生成销售出库，不能继续修改"
            );
        }
    }

    private void lockSources(FreightBill entity, FreightBillRequest request) {
        TreeSet<Long> sourceSalesOrderItemIds = new TreeSet<>();
        entity.getItems().stream()
                .map(FreightBillItem::getSourceSalesOrderItemId)
                .filter(Objects::nonNull)
                .forEach(sourceSalesOrderItemIds::add);
        if (request != null) {
            request.items().stream()
                    .map(FreightBillItemRequest::sourceSalesOrderItemId)
                    .filter(Objects::nonNull)
                    .forEach(sourceSalesOrderItemIds::add);
        }
        if (!sourceSalesOrderItemIds.isEmpty()) {
            sourceAllocationLockService.lockTradeItemSources(
                    List.of(), List.of(), List.copyOf(sourceSalesOrderItemIds)
            );
        }
        TreeSet<Long> sourceItemIds = new TreeSet<>();
        entity.getItems().stream()
                .map(FreightBillItem::getSourceSalesOutboundItemId)
                .filter(Objects::nonNull)
                .forEach(sourceItemIds::add);
        if (request != null) {
            request.items().stream()
                    .map(FreightBillItemRequest::sourceSalesOutboundItemId)
                    .filter(Objects::nonNull)
                    .forEach(sourceItemIds::add);
        }

        TreeSet<Long> sourceIds = new TreeSet<>();
        if (!sourceItemIds.isEmpty()) {
            salesOutboundRepository.findSourceOutboundIdsByItemIds(List.copyOf(sourceItemIds)).stream()
                    .filter(Objects::nonNull)
                    .forEach(sourceIds::add);
        }
        sourceAllocationLockService.lockDocumentSources(
                List.of(),
                List.of(),
                List.copyOf(sourceIds),
                List.of()
        );
    }

    private boolean usesSalesOrderSource(FreightBill entity, FreightBillRequest request) {
        return entity.getSourceSalesOrderId() != null
                || request.sourceSalesOrderId() != null
                || request.items().stream().anyMatch(item -> item.sourceSalesOrderItemId() != null);
    }

    private FreightBillCarrierResolver.CarrierSnapshot resolveCarrier(FreightBillRequest request) {
        if (carrierResolver != null) {
            return carrierResolver.resolve(request.carrierId(), request.carrierCode(), request.carrierName());
        }
        return new FreightBillCarrierResolver.CarrierSnapshot(
                request.carrierId(),
                emptyToNull(request.carrierCode()),
                emptyToNull(request.carrierName()),
                null,
                null
        );
    }

    private void applySettlementCompany(FreightBill entity,
                                        FreightBillRequest request,
                                        FreightBillCarrierResolver.CarrierSnapshot carrier) {
        SettlementCompanySnapshot requestedSettlementCompany = resolveRequestedSettlementCompany(request);
        if (requestedSettlementCompany.id() != null) {
            entity.setSettlementCompanyId(requestedSettlementCompany.id());
            entity.setSettlementCompanyName(requestedSettlementCompany.name());
            return;
        }
        if (carrierResolver == null) {
            return;
        }
        entity.setSettlementCompanyId(carrier.defaultSettlementCompanyId());
        entity.setSettlementCompanyName(carrier.defaultSettlementCompanyName());
    }

    private SettlementCompanySnapshot resolveRequestedSettlementCompany(FreightBillRequest request) {
        Long settlementCompanyId = request.settlementCompanyId();
        if (settlementCompanyId == null) {
            return SettlementCompanySnapshot.EMPTY;
        }
        if (companySettingService == null) {
            return new SettlementCompanySnapshot(
                    settlementCompanyId,
                    emptyToNull(request.settlementCompanyName())
            );
        }
        CompanySetting company = companySettingService.requireActiveSettlementCompany(settlementCompanyId);
        return new SettlementCompanySnapshot(company.getId(), company.getCompanyName());
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

    private record SettlementCompanySnapshot(Long id, String name) {
        private static final SettlementCompanySnapshot EMPTY = new SettlementCompanySnapshot(null, null);
    }

    private Specification<SalesOutbound> importableSalesOutboundStatus(String status) {
        return (root, query, cb) -> {
            String requestedStatus = BusinessDocumentValidator.trimToNull(status);
            if (requestedStatus != null) {
                if (!IMPORTABLE_OUTBOUND_STATUSES.contains(requestedStatus)) {
                    return cb.disjunction();
                }
                return cb.equal(root.get("status"), requestedStatus);
            }
            return root.get("status").in(IMPORTABLE_OUTBOUND_STATUSES);
        };
    }

    private Specification<SalesOutbound> sourceOutboundNotOccupied() {
        return sourceOutboundNotOccupied(null);
    }

    private Specification<SalesOutbound> sourceOutboundNotOccupied(Long currentBillId) {
        return (root, query, cb) -> {
            var subquery = query.subquery(Long.class);
            var bill = subquery.from(FreightBill.class);
            Join<FreightBill, FreightBillItem> item = bill.join("items");
            var sourceItem = subquery.from(SalesOutboundItem.class);
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.isFalse(bill.get("deletedFlag")));
            predicates.add(cb.equal(sourceItem.get("salesOutbound"), root));
            predicates.add(cb.equal(item.get("sourceSalesOutboundItemId"), sourceItem.get("id")));
            if (currentBillId != null) {
                predicates.add(cb.notEqual(bill.get("id"), currentBillId));
            }
            subquery.select(cb.literal(1L)).where(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
            return cb.not(cb.exists(subquery));
        };
    }

    private FreightBillImportCandidateResponse toImportCandidateResponse(SalesOutbound outbound) {
        return new FreightBillImportCandidateResponse(
                outbound.getId(),
                outbound.getOutboundNo(),
                outbound.getSalesOrderNo(),
                outbound.getCustomerId(),
                outbound.getCustomerName(),
                outbound.getProjectId(),
                outbound.getProjectName(),
                outbound.getWarehouseId(),
                outbound.getWarehouseName(),
                outbound.getSettlementCompanyId(),
                outbound.getSettlementCompanyName(),
                outbound.getOutboundDate(),
                outbound.getTotalWeight(),
                outbound.getTotalAmount(),
                outbound.getStatus()
        );
    }

    private VehicleSnapshot resolveVehicle(FreightBillRequest request,
                                           FreightBillCarrierResolver.CarrierSnapshot carrier) {
        Long requestedId = request.vehicleId();
        String requestedPlate = emptyToNull(request.vehiclePlate());
        if (requestedId == null && requestedPlate == null) {
            return VehicleSnapshot.EMPTY;
        }
        if (vehicleRepository == null) {
            return new VehicleSnapshot(requestedId, requestedPlate);
        }
        Vehicle vehicle;
        if (requestedId != null) {
            vehicle = vehicleRepository.findById(requestedId)
                    .orElseThrow(() -> new BusinessException(
                            com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "车辆不存在"));
        } else {
            List<Vehicle> candidates = vehicleRepository.findByCarrierIdOrderBySortOrderAsc(carrier.id()).stream()
                    .filter(candidate -> Objects.equals(requestedPlate, emptyToNull(candidate.getPlate())))
                    .toList();
            if (candidates.size() != 1) {
                throw new BusinessException(
                        com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                        candidates.isEmpty() ? "车辆不存在" : "车牌号对应多辆车，请选择车辆ID"
                );
            }
            vehicle = candidates.get(0);
            log.warn("identity_fallback module=freight-bill field=vehicleId reason=vehicle-plate resolvedId={}",
                    vehicle.getId());
        }
        Long vehicleCarrierId = vehicle.getCarrier() == null ? null : vehicle.getCarrier().getId();
        if (carrier.id() != null && !Objects.equals(carrier.id(), vehicleCarrierId)) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "车辆不属于所选物流商");
        }
        String plate = emptyToNull(vehicle.getPlate());
        if (requestedPlate != null && !Objects.equals(requestedPlate, plate)) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "车辆ID与车牌号不一致");
        }
        return new VehicleSnapshot(vehicle.getId(), plate);
    }

    private record VehicleSnapshot(Long id, String plate) {
        private static final VehicleSnapshot EMPTY = new VehicleSnapshot(null, null);
    }

    @Override
    protected FreightBill saveEntity(FreightBill entity) {
        return repository.save(entity);
    }

    @Override
    protected FreightBill saveUpdatedEntity(FreightBill entity, FreightBillRequest request) {
        lockCurrentFreightBill(entity);
        assertSalesOutboundNotGenerated(entity);
        return saveEntity(entity);
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
