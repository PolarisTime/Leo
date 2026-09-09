package com.leo.erp.finance.ledgeradjustment.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractStatusCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import com.leo.erp.finance.ledgeradjustment.mapper.LedgerAdjustmentMapper;
import com.leo.erp.finance.ledgeradjustment.repository.LedgerAdjustmentRepository;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Service
public class LedgerAdjustmentService extends AbstractStatusCrudService<
        LedgerAdjustment, LedgerAdjustmentRequest, LedgerAdjustmentResponse> {

    private static final String MODULE_KEY = "ledger-adjustment";

    private final LedgerAdjustmentRepository repository;
    private final LedgerAdjustmentMapper mapper;
    private final LedgerAdjustmentApplyService applyService;

    public LedgerAdjustmentService(LedgerAdjustmentRepository repository,
                                   LedgerAdjustmentMapper mapper,
                                   SnowflakeIdGenerator idGenerator,
                                   LedgerAdjustmentApplyService applyService) {
        super(idGenerator);
        this.repository = repository;
        this.mapper = mapper;
        this.applyService = applyService;
    }

    @Transactional(readOnly = true)
    public Page<LedgerAdjustmentResponse> page(PageQuery query,
                                               PageFilter filter,
                                               String direction,
                                               String counterpartyType) {
        Specification<LedgerAdjustment> spec = Specs.<LedgerAdjustment>keywordLike(
                        filter.keyword(),
                        "adjustmentNo",
                        "counterpartyCode",
                        "counterpartyName",
                        "projectName",
                        "adjustmentType",
                        "remark"
                )
                .and(Specs.equalIfPresent("direction", direction))
                .and(Specs.equalIfPresent("counterpartyType", counterpartyType))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.betweenIfPresent("adjustmentDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    @Transactional(readOnly = true)
    public java.util.List<LedgerAdjustmentResponse> search(String keyword, int maxSize) {
        return search(
                keyword,
                new String[]{"adjustmentNo", "counterpartyCode", "counterpartyName", "projectName", "adjustmentType"},
                maxSize,
                null,
                repository
        );
    }

    /**
     * 台账余额不得通过独立调整单直接改写。历史调整单仍可查询，新的余额变更必须
     * 由具有明确业务来源的付款、收款、冲销或其他受控单据产生。
     */
    @Override
    @Transactional
    public LedgerAdjustmentResponse create(LedgerAdjustmentRequest request) {
        throw writeDisabled();
    }

    @Override
    @Transactional
    public LedgerAdjustmentResponse update(Long id, LedgerAdjustmentRequest request) {
        throw writeDisabled();
    }

    @Override
    @Transactional
    public LedgerAdjustmentResponse updateStatus(Long id, String status) {
        throw writeDisabled();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        throw writeDisabled();
    }

    private BusinessException writeDisabled() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "台账调整单已停用，余额调整必须通过有来源的业务或资金单据完成"
        );
    }

    @Override
    protected void validateCreate(LedgerAdjustmentRequest request) {
        if (repository.existsByAdjustmentNoAndDeletedFlagFalse(request.adjustmentNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "调整单号已存在");
        }
    }

    @Override
    protected void validateUpdate(LedgerAdjustment entity, LedgerAdjustmentRequest request) {
        if (!entity.getAdjustmentNo().equals(request.adjustmentNo())
                && repository.existsByAdjustmentNoAndDeletedFlagFalse(request.adjustmentNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "调整单号已存在");
        }
    }

    @Override
    protected LedgerAdjustmentRequest normalizeCreateRequest(LedgerAdjustmentRequest request, long entityId) {
        return new LedgerAdjustmentRequest(
                resolveCreateBusinessNo(entityId),
                request.direction(),
                request.counterpartyType(),
                request.counterpartyId(),
                request.counterpartyCode(),
                request.counterpartyName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.projectId(),
                request.projectName(),
                request.adjustmentDate(),
                request.amount(),
                request.adjustmentType(),
                request.effect(),
                request.status(),
                request.operatorName(),
                request.remark()
        );
    }

    @Override
    protected LedgerAdjustmentRequest normalizeUpdateRequest(LedgerAdjustment entity, LedgerAdjustmentRequest request) {
        return new LedgerAdjustmentRequest(
                entity.getAdjustmentNo(),
                request.direction(),
                request.counterpartyType(),
                request.counterpartyId(),
                request.counterpartyCode(),
                request.counterpartyName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.projectId(),
                request.projectName(),
                request.adjustmentDate(),
                request.amount(),
                request.adjustmentType(),
                request.effect(),
                request.status(),
                request.operatorName(),
                request.remark()
        );
    }

    @Override
    protected LedgerAdjustment newEntity() {
        return new LedgerAdjustment();
    }

    @Override
    protected void assignId(LedgerAdjustment entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<LedgerAdjustment> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<LedgerAdjustment> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "台账调整单不存在";
    }

    @Override
    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    @Override
    protected Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.DRAFT_AUDIT_TRANSITIONS;
    }

    @Override
    protected void apply(LedgerAdjustment entity, LedgerAdjustmentRequest request) {
        applyService.apply(entity, request);
    }

    @Override
    protected LedgerAdjustment saveEntity(LedgerAdjustment entity) {
        return repository.save(entity);
    }

    @Override
    protected LedgerAdjustmentResponse toResponse(LedgerAdjustment entity) {
        return mapper.toResponse(entity);
    }
}
