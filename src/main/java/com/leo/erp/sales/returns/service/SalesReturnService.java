package com.leo.erp.sales.returns.service;

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
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.repository.SalesReturnRepository;
import com.leo.erp.sales.returns.web.dto.SalesReturnRequest;
import com.leo.erp.sales.returns.web.dto.SalesReturnResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class SalesReturnService {

    private static final String[] RETURN_SEARCH_FIELDS = {"returnNo", "salesOrderNo", "customerName", "projectName"};
    private static final CrudStatusGuard<SalesReturn> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Logger log = LoggerFactory.getLogger(SalesReturnService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final SalesReturnRepository repository;
    private final SalesReturnResponseAssembler responseAssembler;
    private final SalesReturnWorkflowService workflowService;

    public SalesReturnService(SalesReturnRepository repository,
                              SnowflakeIdGenerator idGenerator,
                              SalesReturnResponseAssembler responseAssembler,
                              SalesReturnWorkflowService workflowService) {
        this.idGenerator = idGenerator;
        this.repository = repository;
        this.responseAssembler = responseAssembler;
        this.workflowService = workflowService;
    }

    @Transactional(readOnly = true)
    public Page<SalesReturnResponse> page(PageQuery query, PageFilter filter) {
        Specification<SalesReturn> spec = Specs.<SalesReturn>keywordLike(filter.keyword(), RETURN_SEARCH_FIELDS)
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(Specs.equalValueIfPresent("customerId", filter.customerId()))
                .and(Specs.equalValueIfPresent("projectId", filter.projectId()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.betweenIfPresent("returnDate", filter.startDate(), filter.endDate()));
        return pageEntities(query, spec).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SalesReturnResponse detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    @Transactional
    public SalesReturnResponse create(SalesReturnRequest request) {
        SalesReturnResponse created = createReturn(
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Transactional
    public SalesReturnResponse update(Long id, SalesReturnRequest request) {
        SalesReturnResponse updated = updateReturn(id,
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
    }

    @Transactional
    public SalesReturnResponse updateStatus(Long id, String status) {
        SalesReturn salesReturn = requireEntity(id);
        String currentStatus = salesReturn.getStatus();
        SalesReturnResponse response = doUpdateStatus(id, status);
        if (!Objects.equals(currentStatus, response.status())) {
            workflowService.publishStatusChanged(salesReturn, currentStatus, response.status());
        }
        return response;
    }

    /** 资源型审核：对既有销售退货单执行审核（草稿 -> 已审核）。 */
    @Transactional
    public SalesReturnResponse audit(Long id) {
        return updateStatus(id, StatusConstants.AUDITED);
    }

    /** 资源型保存并审核：复用既有 update，强制同事务完成保存与审核。 */
    @Transactional
    public SalesReturnResponse updateAndAudit(Long id, SalesReturnRequest request) {
        return update(id, withAudit(request));
    }

    @Transactional
    public void delete(Long id) {
        SalesReturn entity = requireEntity(id);
        STATUS_GUARD.assertDeleteAllowed(entity);
        entity.setDeletedFlag(true);
        saveEntity(entity);
        workflowService.publishDeleted(entity);
        log.info("{} deleted: id={}", entity.getClass().getSimpleName(), id);
    }

    private SalesReturnResponse createReturn(SalesReturnRequest request) {
        SalesReturn entity = new SalesReturn();
        long entityId = idGenerator.nextId();
        entity.setId(entityId);
        SalesReturnRequest normalized = normalizeCreateRequest(request, entityId);
        validateCreate(normalized);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        SalesReturnResponse response = toSavedResponse(saveCreatedEntity(entity, normalized));
        log.info("{} created: id={}", entity.getClass().getSimpleName(), entityId);
        return response;
    }

    private SalesReturnResponse updateReturn(Long id, SalesReturnRequest request) {
        SalesReturn entity = requireEntity(id);
        STATUS_GUARD.assertEditAllowed(entity, false);
        validateUpdate(entity, request);
        Optional<String> currentStatus = STATUS_GUARD.resolveStatus(entity);
        apply(entity, request);
        STATUS_GUARD.assertRequestStatusTransitionAllowed(entity, currentStatus, allowedStatusTransitions());
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        SalesReturnResponse response = toSavedResponse(saveUpdatedEntity(entity, request));
        log.info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    private SalesReturnResponse doUpdateStatus(Long id, String status) {
        SalesReturn entity = requireEntity(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toSavedResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(allowedStatusTransitions(), currentStatus, nextStatus);
        beforeStatusUpdate(entity, currentStatus, nextStatus);
        STATUS_GUARD.writeStatus(entity, nextStatus);
        SalesReturnResponse response = toSavedResponse(saveEntity(entity));
        log.info("{} status updated: id={}, {} -> {}",
                entity.getClass().getSimpleName(), id, currentStatus, nextStatus);
        return response;
    }

    void validateCreate(SalesReturnRequest request) {
        if (repository.existsByReturnNoAndDeletedFlagFalse(request.returnNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售退货单号已存在");
        }
        String status = request.status() == null ? "" : request.status().trim();
        if (!status.isEmpty() && !StatusConstants.DRAFT.equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新销售退货单只能保存为草稿");
        }
    }

    void validateUpdate(SalesReturn entity, SalesReturnRequest request) {
        if (!entity.getReturnNo().equals(request.returnNo())
                && repository.existsByReturnNoAndDeletedFlagFalse(request.returnNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售退货单号已存在");
        }
    }

    private SalesReturnRequest normalizeCreateRequest(SalesReturnRequest request, long entityId) {
        return new SalesReturnRequest(
                resolveCreateBusinessNo(entityId),
                request.salesOrderNo(),
                request.customerId(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.warehouseId(),
                request.warehouseName(),
                request.returnDate(),
                request.status(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    private SalesReturnRequest withStatus(SalesReturnRequest request, String status) {
        return new SalesReturnRequest(
                request.returnNo(),
                request.salesOrderNo(),
                request.customerId(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.warehouseId(),
                request.warehouseName(),
                request.returnDate(),
                status,
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    private SalesReturnRequest withAudit(SalesReturnRequest request) {
        if (request.audit()) {
            return request;
        }
        return new SalesReturnRequest(
                request.returnNo(),
                request.salesOrderNo(),
                request.customerId(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.warehouseId(),
                request.warehouseName(),
                request.returnDate(),
                request.status(),
                request.remark(),
                request.items(),
                true
        );
    }

    private void apply(SalesReturn entity, SalesReturnRequest request) {
        workflowService.apply(entity, request, idGenerator::nextId);
    }

    private void beforeStatusUpdate(SalesReturn entity, String currentStatus, String nextStatus) {
        workflowService.beforeStatusUpdate(entity, currentStatus, nextStatus);
    }

    private SalesReturn saveEntity(SalesReturn entity) {
        return workflowService.save(entity);
    }

    private SalesReturn saveCreatedEntity(SalesReturn entity, SalesReturnRequest request) {
        return workflowService.saveCreated(entity, request);
    }

    private SalesReturn saveUpdatedEntity(SalesReturn entity, SalesReturnRequest request) {
        return workflowService.saveUpdated(entity, request);
    }

    private SalesReturnResponse toResponse(SalesReturn entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

    private SalesReturnResponse toDetailResponse(SalesReturn entity) {
        return responseAssembler.toDetailResponse(entity);
    }

    private SalesReturnResponse toSavedResponse(SalesReturn entity) {
        return toDetailResponse(entity);
    }

    private SalesReturn requireEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "销售退货单不存在"));
    }

    private SalesReturn requireDetailEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "销售退货单不存在"));
    }

    private Page<SalesReturn> pageEntities(PageQuery query, Specification<SalesReturn> specification) {
        Specification<SalesReturn> effectiveSpec =
                VISIBILITY_POLICY.applyDeletedVisibility(specification, true);
        return repository.findAll(effectiveSpec, query.toPageable("id"));
    }

    private Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.SALES_RETURN_TRANSITIONS;
    }

    private String resolveCreateBusinessNo(Long entityId) {
        if (entityId == null || entityId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "业务单据雪花ID尚未分配");
        }
        return String.valueOf(entityId);
    }
}
