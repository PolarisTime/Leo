package com.leo.erp.statement.freight.service;

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
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapper;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.repository.FreightStatementSummaryAggregate;
import com.leo.erp.statement.freight.repository.FreightStatementSummaryQueryRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class FreightStatementService {

    private static final String[] FREIGHT_STATEMENT_SEARCH_FIELDS = {
            "statementNo",
            "carrierCode",
            "carrierName"
    };
    private static final CrudStatusGuard<FreightStatement> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Logger log = LoggerFactory.getLogger(FreightStatementService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final FreightStatementRepository repository;
    private final FreightStatementSummaryQueryRepository summaryQueryRepository;
    private final FreightStatementWebMapper freightStatementWebMapper;
    private final FreightStatementSourceService freightStatementSourceService;
    private final FreightStatementViewAssembler viewAssembler;
    private final FreightStatementPageAssembler pageAssembler;
    private final FreightStatementWorkflowService workflowService;

    public FreightStatementService(FreightStatementRepository repository,
                                   FreightStatementSummaryQueryRepository summaryQueryRepository,
                                   SnowflakeIdGenerator idGenerator,
                                   FreightStatementWebMapper freightStatementWebMapper,
                                   FreightStatementSourceService freightStatementSourceService,
                                   FreightStatementViewAssembler viewAssembler,
                                   FreightStatementPageAssembler pageAssembler,
                                   FreightStatementWorkflowService workflowService) {
        this.idGenerator = idGenerator;
        this.repository = repository;
        this.summaryQueryRepository = summaryQueryRepository;
        this.freightStatementWebMapper = freightStatementWebMapper;
        this.freightStatementSourceService = freightStatementSourceService;
        this.viewAssembler = viewAssembler;
        this.pageAssembler = pageAssembler;
        this.workflowService = workflowService;
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementView> page(PageQuery query, PageFilter filter) {
        return page(query, filter, null);
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementView> page(PageQuery query, PageFilter filter, String carrierCode) {
        Specification<FreightStatement> spec = buildPageSpecification(filter, carrierCode);
        Page<FreightStatement> entityPage = repository.findAll(spec, query.toPageable("id"));
        return pageAssembler.toViewPage(entityPage);
    }

    @Transactional(readOnly = true)
    public FreightStatementSummaryResponse summary(PageFilter filter, String carrierCode) {
        FreightStatementSummaryAggregate aggregate = summaryQueryRepository.summarize(
                buildPageSpecification(filter, carrierCode)
        );
        return new FreightStatementSummaryResponse(
                aggregate.documentCount(),
                aggregate.totalWeight(),
                aggregate.totalFreight(),
                aggregate.paidAmount(),
                aggregate.unpaidAmount()
        );
    }

    private Specification<FreightStatement> buildPageSpecification(PageFilter filter, String carrierCode) {
        return applyDeletedVisibilityPolicy(
                Specs.<FreightStatement>keywordLike(filter.keyword(), "statementNo", "carrierCode", "carrierName")
                        .and(Specs.equalValueIfPresent("carrierId", filter.carrierId()))
                        .and(Specs.equalIfPresent("carrierCode", carrierCode))
                        .and(Specs.equalIfPresent("carrierName", filter.name()))
                        .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                        .and(Specs.documentStatus(filter.status()))
                        .and(Specs.betweenIfPresent("endDate", filter.startDate(), filter.endDate()))
        );
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementResponse> responsePage(PageQuery query, PageFilter filter) {
        return page(query, filter).map(freightStatementWebMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementResponse> responsePage(PageQuery query, PageFilter filter, String carrierCode) {
        return page(query, filter, carrierCode).map(freightStatementWebMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<FreightStatementView> search(String keyword, int maxSize) {
        Specification<FreightStatement> spec = combineSpecifications(
                VISIBILITY_POLICY.applyDeletedVisibility(null, false),
                Specs.keywordLike(keyword, FREIGHT_STATEMENT_SEARCH_FIELDS)
        );
        return repository.findAll(spec, PageRequest.of(0, maxSize))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FreightStatementResponse> responseSearch(String keyword, int maxSize) {
        return search(keyword, maxSize).stream()
                .map(freightStatementWebMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FreightStatementResponse responseDetail(Long id) {
        return freightStatementWebMapper.toResponse(detail(id));
    }

    @Transactional(readOnly = true)
    public FreightStatementView detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    @Transactional
    public FreightStatementResponse responseCreate(FreightStatementRequest request) {
        return freightStatementWebMapper.toResponse(create(freightStatementWebMapper.toCommand(request)));
    }

    @Transactional
    public FreightStatementResponse responseUpdate(Long id, FreightStatementRequest request) {
        return freightStatementWebMapper.toResponse(update(id, freightStatementWebMapper.toCommand(request)));
    }

    @Transactional
    public FreightStatementResponse responseUpdateStatus(Long id, String status) {
        return freightStatementWebMapper.toResponse(updateStatus(id, status));
    }

    @Transactional
    public FreightStatementView create(FreightStatementCommand command) {
        FreightStatementView created = createStatement(
                command.audit() ? withStatus(command, StatusConstants.DRAFT) : command);
        if (command.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Transactional
    public FreightStatementView update(Long id, FreightStatementCommand command) {
        FreightStatementView updated = updateStatement(id,
                command.audit() ? withStatus(command, StatusConstants.DRAFT) : command);
        if (command.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
    }

    @Transactional
    public FreightStatementView updateStatus(Long id, String status) {
        FreightStatement statement = requireEntity(id);
        String currentStatus = statement.getStatus();
        FreightStatementView response = doUpdateStatus(id, status);
        if (!Objects.equals(currentStatus, response.status())) {
            workflowService.publishStatusChanged(statement, currentStatus, response.status());
        }
        return response;
    }

    @Transactional
    public void delete(Long id) {
        FreightStatement entity = requireEntity(id);
        STATUS_GUARD.assertDeleteAllowed(entity);
        beforeDelete(entity);
        entity.setDeletedFlag(true);
        saveEntity(entity);
        afterDelete(entity);
        log.info("{} deleted: id={}", entity.getClass().getSimpleName(), id);
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementCandidateResponse> candidatePage(PageQuery query, PageFilter filter) {
        return freightStatementSourceService.candidatePage(query, filter);
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementCandidateResponse> candidatePage(PageQuery query,
                                                                 PageFilter filter,
                                                                 String carrierCode) {
        return freightStatementSourceService.candidatePage(query, filter, carrierCode);
    }

    protected FreightStatementView toDetailResponse(FreightStatement entity) {
        return viewAssembler.toDetailView(entity);
    }

    protected FreightStatementView toSavedResponse(FreightStatement entity) {
        return toDetailResponse(entity);
    }

    protected void validateCreate(FreightStatementCommand command) {
        if (repository.existsByStatementNoAndDeletedFlagFalse(command.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流对账单号已存在");
        }
        String status = normalizeText(command.status());
        if (status != null && !StatusConstants.DRAFT.equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新物流对账单只能保存为草稿");
        }
    }

    protected void validateUpdate(FreightStatement entity, FreightStatementCommand command) {
        if (!entity.getStatementNo().equals(command.statementNo())
                && repository.existsByStatementNoAndDeletedFlagFalse(command.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流对账单号已存在");
        }
    }

    protected FreightStatementCommand normalizeCreateRequest(FreightStatementCommand command, long entityId) {
        return new FreightStatementCommand(
                resolveCreateBusinessNo(entityId),
                command.carrierCode(),
                command.carrierName(),
                command.settlementCompanyId(),
                command.settlementCompanyName(),
                command.startDate(),
                command.endDate(),
                command.totalWeight(),
                command.totalFreight(),
                command.paidAmount(),
                command.unpaidAmount(),
                command.status(),
                command.attachment(),
                command.remark(),
                command.items(),
                command.carrierId(),
                command.audit()
        );
    }

    protected FreightStatementCommand normalizeUpdateRequest(FreightStatement entity, FreightStatementCommand command) {
        String requestedStatus = normalizeText(command.status());
        if (requestedStatus != null && !Objects.equals(entity.getStatus(), requestedStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流对账单状态只能通过审核或反审核操作变更");
        }
        return new FreightStatementCommand(
                entity.getStatementNo(),
                command.carrierCode(),
                command.carrierName(),
                command.settlementCompanyId(),
                command.settlementCompanyName(),
                command.startDate(),
                command.endDate(),
                command.totalWeight(),
                command.totalFreight(),
                command.paidAmount(),
                command.unpaidAmount(),
                command.status(),
                command.attachment(),
                command.remark(),
                command.items(),
                command.carrierId(),
                command.audit()
        );
    }

    private FreightStatementCommand withStatus(FreightStatementCommand command, String status) {
        return new FreightStatementCommand(
                command.statementNo(), command.carrierCode(), command.carrierName(),
                command.settlementCompanyId(), command.settlementCompanyName(), command.startDate(), command.endDate(),
                command.totalWeight(), command.totalFreight(), command.paidAmount(), command.unpaidAmount(), status,
                command.attachment(), command.remark(), command.items(), command.carrierId(), command.audit()
        );
    }

    protected FreightStatement newEntity() {
        return new FreightStatement();
    }

    protected void assignId(FreightStatement entity, Long id) {
        entity.setId(id);
    }

    protected Optional<FreightStatement> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    protected Optional<FreightStatement> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    protected String notFoundMessage() {
        return "物流对账单不存在";
    }

    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    protected Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.DRAFT_AUDIT_TRANSITIONS;
    }

    private boolean allowProtectedStatusUpdate(FreightStatement entity, FreightStatementCommand command) {
        // 基类 allowProtectedStatusUpdate 默认 false：受保护状态单据不允许普通编辑。
        return false;
    }

    protected void apply(FreightStatement entity, FreightStatementCommand command) {
        workflowService.apply(entity, command, this::nextId);
    }

    protected void beforeStatusUpdate(FreightStatement entity, String currentStatus, String nextStatus) {
        workflowService.beforeStatusUpdate(entity, currentStatus, nextStatus);
    }

    protected void beforeDelete(FreightStatement entity) {
        workflowService.assertDeleteAllowed(entity);
    }

    protected void afterDelete(FreightStatement entity) {
        workflowService.publishDeleted(entity);
    }

    protected FreightStatement saveEntity(FreightStatement entity) {
        return workflowService.save(entity);
    }

    protected FreightStatement saveCreatedEntity(FreightStatement entity, FreightStatementCommand command) {
        return workflowService.saveCreated(entity, command);
    }

    protected FreightStatement saveUpdatedEntity(FreightStatement entity, FreightStatementCommand command) {
        return workflowService.saveUpdated(entity, command);
    }

    protected FreightStatementView toResponse(FreightStatement entity) {
        return viewAssembler.toDetailView(entity);
    }

    /**
     * 基类 create 的显式内联：雪花 ID → 归一化 → 校验 → 应用 → 终态双写守卫 → 保存。
     */
    private FreightStatementView createStatement(FreightStatementCommand command) {
        FreightStatement entity = newEntity();
        long entityId = idGenerator.nextId();
        assignId(entity, entityId);
        FreightStatementCommand normalized = normalizeCreateRequest(command, entityId);
        validateCreate(normalized);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        FreightStatementView response = toSavedResponse(saveCreatedEntity(entity, normalized));
        log.info("{} created: id={}", entity.getClass().getSimpleName(), entityId);
        return response;
    }

    /**
     * 基类 update 的显式内联，状态断言序列逐字保持：
     * 编辑状态守卫 → 更新校验 → 快照当前状态 → 应用请求 →
     * assertRequestStatusTransitionAllowed → allowRequestToWriteFinalStatus 分支下的
     * assertRequestDidNotWriteFinalStatus → 保存。
     */
    private FreightStatementView updateStatement(Long id, FreightStatementCommand command) {
        FreightStatement entity = requireEntity(id);
        FreightStatementCommand normalized = normalizeUpdateRequest(entity, command);
        STATUS_GUARD.assertEditAllowed(entity, allowProtectedStatusUpdate(entity, normalized));
        validateUpdate(entity, normalized);
        Optional<String> currentStatus = STATUS_GUARD.resolveStatus(entity);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestStatusTransitionAllowed(entity, currentStatus, allowedStatusTransitions());
        // 基类 allowRequestToWriteFinalStatus 默认 false：普通保存一律拒绝终态写入。
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        FreightStatementView response = toSavedResponse(saveUpdatedEntity(entity, normalized));
        log.info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    /**
     * 基类 updateStatus 的显式内联：等值短路 → 迁移表校验 → beforeStatusUpdate → 写状态 → 状态保存。
     */
    private FreightStatementView doUpdateStatus(Long id, String status) {
        FreightStatement entity = requireEntity(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toSavedResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(allowedStatusTransitions(), currentStatus, nextStatus);
        beforeStatusUpdate(entity, currentStatus, nextStatus);
        STATUS_GUARD.writeStatus(entity, nextStatus);
        FreightStatementView response = toSavedResponse(saveEntity(entity));
        log.info(
                "{} status updated: id={}, {} -> {}",
                entity.getClass().getSimpleName(),
                id,
                currentStatus,
                nextStatus
        );
        return response;
    }

    private FreightStatement requireEntity(Long id) {
        return findActiveEntity(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    private FreightStatement requireDetailEntity(Long id) {
        if (allowViewingDeletedRecords()) {
            return findVisibleEntity(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        }
        return requireEntity(id);
    }

    private Specification<FreightStatement> applyDeletedVisibilityPolicy(Specification<FreightStatement> specification) {
        return VISIBILITY_POLICY.applyDeletedVisibility(specification, allowViewingDeletedRecords());
    }

    private Specification<FreightStatement> combineSpecifications(Specification<FreightStatement> left,
                                                                 Specification<FreightStatement> right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.and(right);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
