package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.service.CrudVisibilityPolicy;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class SalesOutboundService {

    private static final String[] PRODUCT_SEARCH_FIELDS = {"materialCode", "brand", "material", "spec"};
    private static final String[] OUTBOUND_SEARCH_FIELDS = {"outboundNo", "salesOrderNo", "customerName", "projectName"};
    private static final CrudStatusGuard<SalesOutbound> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Logger log = LoggerFactory.getLogger(SalesOutboundService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final SalesOutboundRepository repository;
    private final SalesOutboundResponseAssembler responseAssembler;
    private final SalesOutboundWorkflowService workflowService;
    private final SalesOutboundDeleteRollbackService deleteRollbackService;
    private final SalesOutboundImportedUpdatePolicy importedUpdatePolicy;

    @Autowired
    public SalesOutboundService(SalesOutboundRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                SalesOutboundResponseAssembler responseAssembler,
                                SalesOutboundWorkflowService workflowService,
                                SalesOutboundDeleteRollbackService deleteRollbackService) {
        this.idGenerator = idGenerator;
        this.repository = repository;
        this.responseAssembler = responseAssembler;
        this.workflowService = workflowService;
        this.deleteRollbackService = deleteRollbackService;
        this.importedUpdatePolicy = new SalesOutboundImportedUpdatePolicy();
    }

    @Transactional(readOnly = true)
    public Page<SalesOutboundResponse> page(PageQuery query, PageFilter filter, String productKeyword) {
        Specification<SalesOutbound> spec = Specs.<SalesOutbound>keywordLike(filter.keyword(), OUTBOUND_SEARCH_FIELDS)
                .and(Specs.collectionKeywordLike(productKeyword, "items", PRODUCT_SEARCH_FIELDS))
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(Specs.equalValueIfPresent("customerId", filter.customerId()))
                .and(Specs.equalValueIfPresent("projectId", filter.projectId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.betweenIfPresent("outboundDate", filter.startDate(), filter.endDate()));
        return pageEntities(query, spec).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SalesOutboundResponse> search(String keyword, int maxSize) {
        return search(keyword, OUTBOUND_SEARCH_FIELDS, maxSize);
    }

    @Transactional(readOnly = true)
    public SalesOutboundResponse detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    @Transactional
    public SalesOutboundResponse create(SalesOutboundRequest request) {
        SalesOutboundResponse created = createOutbound(
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Transactional
    public SalesOutboundResponse update(Long id, SalesOutboundRequest request) {
        SalesOutboundResponse updated = updateOutbound(id,
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
    }

    @Transactional
    public SalesOutboundResponse updateStatus(Long id, String status) {
        SalesOutbound outbound = requireEntity(id);
        String currentStatus = outbound.getStatus();
        SalesOutboundResponse response = doUpdateStatus(id, status);
        if (!Objects.equals(currentStatus, response.status())) {
            workflowService.publishStatusChanged(outbound, currentStatus, response.status());
        }
        return response;
    }

    /** 资源型审核：对既有销售出库执行审核（草稿 -> 已审核），复用既有状态迁移与联动链路。 */
    @Transactional
    public SalesOutboundResponse audit(Long id) {
        return updateStatus(id, StatusConstants.AUDITED);
    }

    /** 资源型保存并审核：复用既有 update，强制同事务完成保存与审核。 */
    @Transactional
    public SalesOutboundResponse updateAndAudit(Long id, SalesOutboundRequest request) {
        return update(id, withAudit(request));
    }

    @Transactional
    public void delete(Long id) {
        SalesOutbound entity = requireEntity(id);
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
    private SalesOutboundResponse createOutbound(SalesOutboundRequest request) {
        SalesOutbound entity = newEntity();
        long entityId = idGenerator.nextId();
        assignId(entity, entityId);
        SalesOutboundRequest normalized = normalizeCreateRequest(request, entityId);
        validateCreate(normalized);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        SalesOutboundResponse response = toSavedResponse(saveCreatedEntity(entity, normalized));
        log.info("{} created: id={}", entity.getClass().getSimpleName(), entityId);
        return response;
    }

    /**
     * 基类 update 的显式内联，状态断言序列逐字保持：
     * 编辑状态守卫 → 更新校验 → 快照当前状态 → 应用请求 →
     * assertRequestStatusTransitionAllowed → allowRequestToWriteFinalStatus 分支下的
     * assertRequestDidNotWriteFinalStatus → 保存。
     */
    private SalesOutboundResponse updateOutbound(Long id, SalesOutboundRequest request) {
        SalesOutbound entity = requireEntity(id);
        SalesOutboundRequest normalized = normalizeUpdateRequest(entity, request);
        STATUS_GUARD.assertEditAllowed(entity, allowProtectedStatusUpdate(entity, normalized));
        validateUpdate(entity, normalized);
        Optional<String> currentStatus = STATUS_GUARD.resolveStatus(entity);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestStatusTransitionAllowed(entity, currentStatus, allowedStatusTransitions());
        // 基类 allowRequestToWriteFinalStatus 默认 false：普通保存一律拒绝终态写入。
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        SalesOutboundResponse response = toSavedResponse(saveUpdatedEntity(entity, normalized));
        log.info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    /**
     * 基类 updateStatus 的显式内联：等值短路 → 迁移表校验 → beforeStatusUpdate → 写状态 → 状态保存。
     */
    private SalesOutboundResponse doUpdateStatus(Long id, String status) {
        SalesOutbound entity = requireEntity(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toSavedResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(allowedStatusTransitions(), currentStatus, nextStatus);
        beforeStatusUpdate(entity, currentStatus, nextStatus);
        STATUS_GUARD.writeStatus(entity, nextStatus);
        SalesOutboundResponse response = toSavedResponse(saveEntity(entity));
        log.info(
                "{} status updated: id={}, {} -> {}",
                entity.getClass().getSimpleName(),
                id,
                currentStatus,
                nextStatus
        );
        return response;
    }

    protected void validateCreate(SalesOutboundRequest request) {
        if (repository.existsByOutboundNoAndDeletedFlagFalse(request.outboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库单号已存在");
        }
        String status = request.status() == null ? "" : request.status().trim();
        if (!status.isEmpty() && !StatusConstants.DRAFT.equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新销售出库只能保存为草稿");
        }
        if (request.salesOrderNo() == null || request.salesOrderNo().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库必须从已审核销售订单导入");
        }
    }

    protected void validateUpdate(SalesOutbound entity, SalesOutboundRequest request) {
        if (!entity.getOutboundNo().equals(request.outboundNo()) && repository.existsByOutboundNoAndDeletedFlagFalse(request.outboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库单号已存在");
        }
    }

    protected SalesOutboundRequest normalizeCreateRequest(SalesOutboundRequest request, long entityId) {
        return new SalesOutboundRequest(
                resolveCreateBusinessNo(entityId),
                request.salesOrderNo(),
                request.customerId(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.warehouseId(),
                request.warehouseName(),
                request.outboundDate(),
                request.status(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    private SalesOutboundRequest withStatus(SalesOutboundRequest request, String status) {
        return new SalesOutboundRequest(
                request.outboundNo(),
                request.salesOrderNo(),
                request.customerId(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.warehouseId(),
                request.warehouseName(),
                request.outboundDate(),
                status,
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    private SalesOutboundRequest withAudit(SalesOutboundRequest request) {
        if (request.audit()) {
            return request;
        }
        return new SalesOutboundRequest(
                request.outboundNo(),
                request.salesOrderNo(),
                request.customerId(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.warehouseId(),
                request.warehouseName(),
                request.outboundDate(),
                request.status(),
                request.remark(),
                request.items(),
                true
        );
    }

    protected SalesOutboundRequest normalizeUpdateRequest(SalesOutbound entity, SalesOutboundRequest request) {
        return importedUpdatePolicy.normalizeUpdateRequest(entity, request);
    }

    protected SalesOutbound newEntity() {
        return new SalesOutbound();
    }

    protected void assignId(SalesOutbound entity, Long id) {
        entity.setId(id);
    }

    protected Optional<SalesOutbound> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    protected Optional<SalesOutbound> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    protected String notFoundMessage() {
        return "销售出库不存在";
    }

    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    protected Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.SALES_OUTBOUND_TRANSITIONS;
    }

    private boolean allowProtectedStatusUpdate(SalesOutbound entity, SalesOutboundRequest request) {
        // 基类 allowProtectedStatusUpdate 默认 false：受保护状态单据不允许普通编辑。
        return false;
    }

    protected void apply(SalesOutbound entity, SalesOutboundRequest request) {
        workflowService.apply(entity, request, this::nextId);
    }

    protected void beforeStatusUpdate(SalesOutbound entity, String currentStatus, String nextStatus) {
        workflowService.beforeStatusUpdate(entity, currentStatus, nextStatus);
    }

    protected void beforeDelete(SalesOutbound entity) {
        workflowService.lockSourceSalesOrderItems(entity.getItems(), List.of());
        deleteRollbackService.beforeDelete(entity);
    }

    protected void afterDelete(SalesOutbound entity) {
        workflowService.publishDeleted(entity);
    }

    protected SalesOutbound saveEntity(SalesOutbound entity) {
        return workflowService.save(entity);
    }

    protected SalesOutbound saveCreatedEntity(SalesOutbound entity, SalesOutboundRequest request) {
        return workflowService.saveCreated(entity, request);
    }

    protected SalesOutbound saveUpdatedEntity(SalesOutbound entity, SalesOutboundRequest request) {
        return workflowService.saveUpdated(entity, request);
    }

    protected SalesOutboundResponse toResponse(SalesOutbound entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

    protected SalesOutboundResponse toDetailResponse(SalesOutbound entity) {
        return responseAssembler.toDetailResponse(entity);
    }

    protected SalesOutboundResponse toSavedResponse(SalesOutbound entity) {
        return toDetailResponse(entity);
    }

    private SalesOutbound requireEntity(Long id) {
        return findActiveEntity(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    private SalesOutbound requireDetailEntity(Long id) {
        if (allowViewingDeletedRecords()) {
            return findVisibleEntity(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        }
        return requireEntity(id);
    }

    private Page<SalesOutbound> pageEntities(PageQuery query, Specification<SalesOutbound> specification) {
        Specification<SalesOutbound> effectiveSpec =
                VISIBILITY_POLICY.applyDeletedVisibility(specification, allowViewingDeletedRecords());
        return repository.findAll(effectiveSpec, query.toPageable("id"));
    }

    private List<SalesOutboundResponse> search(String keyword, String[] searchFields, int maxSize) {
        Specification<SalesOutbound> spec = combineSpecifications(
                VISIBILITY_POLICY.applyDeletedVisibility(null, false),
                Specs.keywordLike(keyword, searchFields)
        );
        return repository.findAll(spec, PageRequest.of(0, maxSize))
                .map(this::toResponse)
                .toList();
    }

    private Specification<SalesOutbound> combineSpecifications(Specification<SalesOutbound> left,
                                                              Specification<SalesOutbound> right) {
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
        if (entityId == null || entityId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "业务单据雪花ID尚未分配");
        }
        return String.valueOf(entityId);
    }
}
