package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.service.CrudVisibilityPolicy;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.purchase.inbound.web.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class PurchaseInboundService {

    private static final String[] INBOUND_SEARCH_FIELDS = {"inboundNo", "purchaseOrderNo", "supplierName"};
    private static final CrudStatusGuard<PurchaseInbound> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Logger log = LoggerFactory.getLogger(PurchaseInboundService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final PurchaseInboundRepository repository;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseInboundApplyService applyService;
    private final PurchaseInboundResponseAssembler responseAssembler;
    private final PurchaseInboundMutationGuardService mutationGuardService;
    private final PurchaseInboundWorkflowService workflowService;

    @Autowired
    public PurchaseInboundService(PurchaseInboundRepository repository,
                                  SnowflakeIdGenerator idGenerator,
                                  PurchaseInboundMapper purchaseInboundMapper,
                                  PurchaseInboundApplyService applyService,
                                  PurchaseInboundResponseAssembler responseAssembler,
                                  PurchaseInboundMutationGuardService mutationGuardService,
                                  PurchaseInboundWorkflowService workflowService) {
        this.idGenerator = idGenerator;
        this.repository = repository;
        this.purchaseInboundMapper = purchaseInboundMapper;
        this.applyService = applyService;
        this.responseAssembler = responseAssembler;
        this.mutationGuardService = mutationGuardService;
        this.workflowService = workflowService;
    }

    @Transactional(readOnly = true)
    public PurchaseInboundResponse detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    @Transactional(readOnly = true)
    public Page<PurchaseInboundResponse> page(PageQuery query, PageFilter filter) {
        Specification<PurchaseInbound> spec = Specs.<PurchaseInbound>keywordLike(
                        filter.keyword(),
                        "inboundNo", "purchaseOrderNo", "supplierName"
                )
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.betweenIfPresent(
                        "inboundDate", filter.startDate(), filter.endDate()
                ));
        Page<PurchaseInbound> page = pageEntities(query, spec);
        Map<Long, PurchaseInboundItemRepository.InboundWeightSummary> weightSummaryMap =
                responseAssembler.loadInboundWeightSummaryMap(page.getContent());
        return page.map(inbound -> responseAssembler.toListResponse(inbound, weightSummaryMap.get(inbound.getId())));
    }

    @Transactional(readOnly = true)
    public java.util.List<PurchaseInboundResponse> search(String keyword, int maxSize) {
        java.util.List<PurchaseInboundResponse> responses = search(keyword, INBOUND_SEARCH_FIELDS, maxSize);
        Map<Long, PurchaseInboundItemRepository.InboundWeightSummary> weightSummaryMap =
                responseAssembler.loadInboundWeightSummaryMapByIds(responses.stream()
                        .map(PurchaseInboundResponse::id)
                        .distinct()
                        .toList());
        return responses.stream()
                .map(response -> responseAssembler.withInboundWeightSummary(response, weightSummaryMap.get(response.id())))
                .toList();
    }

    @Transactional
    public PurchaseInboundResponse create(PurchaseInboundRequest request) {
        PurchaseInboundResponse created = createOrder(
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Transactional
    public PurchaseInboundResponse update(Long id, PurchaseInboundRequest request) {
        PurchaseInboundResponse updated = updateOrder(id,
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
    }

    @Transactional
    public PurchaseInboundResponse updateStatus(Long id, String status) {
        PurchaseInbound inbound = requireEntity(id);
        String currentStatus = inbound.getStatus();
        PurchaseInboundResponse response = doUpdateStatus(id, status);
        if (!Objects.equals(currentStatus, response.status())) {
            workflowService.publishStatusChanged(inbound, currentStatus, response.status());
        }
        return response;
    }

    @Transactional
    public void delete(Long id) {
        PurchaseInbound entity = requireEntity(id);
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
    private PurchaseInboundResponse createOrder(PurchaseInboundRequest request) {
        PurchaseInbound entity = new PurchaseInbound();
        long entityId = idGenerator.nextId();
        entity.setId(entityId);
        PurchaseInboundRequest normalized = normalizeCreateRequest(request, entityId);
        validateCreate(normalized);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        PurchaseInboundResponse response = toDetailResponse(saveCreatedEntity(entity, normalized));
        log.info("{} created: id={}", entity.getClass().getSimpleName(), entityId);
        return response;
    }

    /**
     * 基类 update 的显式内联，状态断言序列逐字保持：
     * 编辑状态守卫 → 更新校验 → 快照当前状态 → 应用请求 →
     * assertRequestStatusTransitionAllowed → allowRequestToWriteFinalStatus 分支下的
     * assertRequestDidNotWriteFinalStatus → 保存。
     */
    private PurchaseInboundResponse updateOrder(Long id, PurchaseInboundRequest request) {
        PurchaseInbound entity = requireEntity(id);
        PurchaseInboundRequest normalized = normalizeUpdateRequest(entity, request);
        STATUS_GUARD.assertEditAllowed(entity, false);
        validateUpdate(entity, normalized);
        Optional<String> currentStatus = STATUS_GUARD.resolveStatus(entity);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestStatusTransitionAllowed(entity, currentStatus, allowedStatusTransitions());
        // 基类 allowRequestToWriteFinalStatus 默认 false：普通保存一律拒绝终态写入。
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        PurchaseInboundResponse response = toDetailResponse(saveUpdatedEntity(entity, normalized));
        log.info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    /**
     * 基类 updateStatus 的显式内联：等值短路 → 迁移表校验 → beforeStatusUpdate → 写状态 → 状态保存。
     */
    private PurchaseInboundResponse doUpdateStatus(Long id, String status) {
        PurchaseInbound entity = requireEntity(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toDetailResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(allowedStatusTransitions(), currentStatus, nextStatus);
        beforeStatusUpdate(entity, currentStatus, nextStatus);
        STATUS_GUARD.writeStatus(entity, nextStatus);
        PurchaseInboundResponse response = toDetailResponse(saveStatusEntity(entity));
        log.info(
                "{} status updated: id={}, {} -> {}",
                entity.getClass().getSimpleName(),
                id,
                currentStatus,
                nextStatus
        );
        return response;
    }

    private PurchaseInboundResponse toDetailResponse(PurchaseInbound inbound) {
        return responseAssembler.toDetailResponse(inbound);
    }

    private void validateCreate(PurchaseInboundRequest request) {
        if (repository.existsByInboundNoAndDeletedFlagFalse(request.inboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购入库单号已存在");
        }
    }

    private void validateUpdate(PurchaseInbound inbound, PurchaseInboundRequest request) {
        boolean noChanged = !inbound.getInboundNo().equals(request.inboundNo());
        boolean noExists = repository.existsByInboundNoAndDeletedFlagFalse(
                request.inboundNo()
        );
        if (noChanged && noExists) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购入库单号已存在");
        }
    }

    private PurchaseInboundRequest normalizeCreateRequest(PurchaseInboundRequest request, long entityId) {
        return new PurchaseInboundRequest(
                resolveCreateBusinessNo(entityId),
                request.purchaseOrderNo(),
                request.supplierId(),
                request.supplierCode(),
                request.supplierName(),
                request.warehouseId(),
                request.warehouseName(),
                request.inboundDate(),
                request.settlementMode(),
                request.status(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    private PurchaseInboundRequest withStatus(PurchaseInboundRequest request, String status) {
        return new PurchaseInboundRequest(
                request.inboundNo(),
                request.purchaseOrderNo(),
                request.supplierId(),
                request.supplierCode(),
                request.supplierName(),
                request.warehouseId(),
                request.warehouseName(),
                request.inboundDate(),
                request.settlementMode(),
                status,
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    private PurchaseInboundRequest normalizeUpdateRequest(PurchaseInbound entity, PurchaseInboundRequest request) {
        return new PurchaseInboundRequest(
                entity.getInboundNo(),
                request.purchaseOrderNo(),
                request.supplierId() == null ? entity.getSupplierId() : request.supplierId(),
                request.supplierCode() == null || request.supplierCode().isBlank()
                        ? entity.getSupplierCode()
                        : request.supplierCode(),
                request.supplierName(),
                request.warehouseId() == null ? entity.getWarehouseId() : request.warehouseId(),
                request.warehouseName(),
                request.inboundDate(),
                request.settlementMode(),
                request.status(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    private void apply(PurchaseInbound inbound, PurchaseInboundRequest request) {
        mutationGuardService.lockSourcePurchaseOrderItems(inbound, request);
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "采购入库状态",
                StatusConstants.ALLOWED_PURCHASE_INBOUND_STATUS
        );
        mutationGuardService.assertSaveDoesNotChangeStatus(inbound, nextStatus);
        inbound.setInboundNo(request.inboundNo());
        inbound.setPurchaseOrderNo(request.purchaseOrderNo());
        inbound.setSupplierId(request.supplierId());
        inbound.setSupplierCode(request.supplierCode());
        inbound.setSupplierName(request.supplierName());
        inbound.setWarehouseId(request.warehouseId());
        inbound.setInboundDate(request.inboundDate());
        inbound.setStatus(nextStatus);
        inbound.setRemark(request.remark());
        applyService.applyItems(inbound, request, this::nextId);
    }

    private void beforeDelete(PurchaseInbound inbound) {
        mutationGuardService.assertDeletionAllowed(inbound);
    }

    private void afterDelete(PurchaseInbound inbound) {
        workflowService.afterDelete(inbound);
    }

    private void beforeStatusUpdate(PurchaseInbound inbound, String currentStatus, String nextStatus) {
        mutationGuardService.prepareStatusTransition(inbound, currentStatus, nextStatus);
        inbound.setSourcePurchaseOrderReopenAllowed(
                StatusConstants.DRAFT.equals(nextStatus)
                        && (StatusConstants.AUDITED.equals(currentStatus)
                        || StatusConstants.INBOUND_COMPLETED.equals(currentStatus))
        );
    }

    private PurchaseInbound saveEntity(PurchaseInbound entity) {
        return repository.save(entity);
    }

    private PurchaseInbound saveCreatedEntity(PurchaseInbound entity, PurchaseInboundRequest request) {
        return workflowService.saveCreated(entity, request);
    }

    private PurchaseInbound saveUpdatedEntity(PurchaseInbound entity, PurchaseInboundRequest request) {
        return workflowService.saveUpdated(entity, request);
    }

    private PurchaseInbound saveStatusEntity(PurchaseInbound entity) {
        return workflowService.saveStatus(entity);
    }

    private PurchaseInboundResponse toResponse(PurchaseInbound entity) {
        return purchaseInboundMapper.toResponse(entity);
    }

    private PurchaseInbound requireEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    private PurchaseInbound requireDetailEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    private String notFoundMessage() {
        return "采购入库不存在";
    }

    private Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.PURCHASE_INBOUND_TRANSITIONS;
    }

    private Page<PurchaseInbound> pageEntities(PageQuery query, Specification<PurchaseInbound> specification) {
        Specification<PurchaseInbound> effectiveSpec =
                VISIBILITY_POLICY.applyDeletedVisibility(specification, allowViewingDeletedRecords());
        return repository.findAll(effectiveSpec, query.toPageable("id"));
    }

    private java.util.List<PurchaseInboundResponse> search(String keyword, String[] searchFields, int maxSize) {
        Specification<PurchaseInbound> spec = combineSpecifications(
                VISIBILITY_POLICY.applyDeletedVisibility(null, false),
                Specs.keywordLike(keyword, searchFields)
        );
        return repository.findAll(spec, PageRequest.of(0, maxSize))
                .map(this::toResponse)
                .toList();
    }

    private Specification<PurchaseInbound> combineSpecifications(Specification<PurchaseInbound> left,
                                                                 Specification<PurchaseInbound> right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.and(right);
    }

    private long nextId() {
        return idGenerator.nextId();
    }

    private String resolveCreateBusinessNo(Long entityId) {
        if (entityId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "业务单据雪花ID尚未分配");
        }
        return String.valueOf(entityId);
    }

    private boolean allowViewingDeletedRecords() {
        return true;
    }
}
