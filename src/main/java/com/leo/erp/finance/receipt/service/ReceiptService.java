package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.service.CrudVisibilityPolicy;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptPurposes;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import com.leo.erp.finance.receipt.mapper.ReceiptMapper;
import com.leo.erp.finance.receipt.repository.ReceiptRepository;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import com.leo.erp.finance.common.service.SupplierPrepaymentBalanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
public class ReceiptService {

    private static final String[] RECEIPT_SEARCH_FIELDS = {
            "receiptNo",
            "customerName",
            "projectName"
    };
    private static final CrudStatusGuard<Receipt> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final ReceiptRepository receiptRepository;
    private final ReceiptMapper receiptMapper;
    private final ReceiptApplyService applyService;
    private final ReceiptAllocationService receiptAllocationService;
    private final ReceiptAllocationResponseAssembler allocationResponseAssembler;
    private final ReceiptSettlementSyncService settlementSyncService;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final SupplierPrepaymentBalanceService supplierPrepaymentBalanceService;

    @Autowired
    public ReceiptService(ReceiptRepository receiptRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          ReceiptMapper receiptMapper,
                          ReceiptApplyService applyService,
                          ReceiptAllocationService receiptAllocationService,
                          ReceiptAllocationResponseAssembler allocationResponseAssembler,
                          ReceiptSettlementSyncService settlementSyncService,
                          SourceAllocationLockService sourceAllocationLockService,
                          SupplierPrepaymentBalanceService supplierPrepaymentBalanceService) {
        this.idGenerator = snowflakeIdGenerator;
        this.receiptRepository = receiptRepository;
        this.receiptMapper = receiptMapper;
        this.applyService = applyService;
        this.receiptAllocationService = receiptAllocationService;
        this.allocationResponseAssembler = allocationResponseAssembler;
        this.settlementSyncService = settlementSyncService;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.supplierPrepaymentBalanceService = supplierPrepaymentBalanceService;
    }

    @Transactional(readOnly = true)
    public Page<ReceiptResponse> page(PageQuery query, PageFilter filter) {
        Specification<Receipt> spec = Specs.<Receipt>keywordLike(
                        filter.keyword(),
                        "receiptNo",
                        "counterpartyCode",
                        "counterpartyName",
                        "customerName",
                        "projectName"
                )
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("counterpartyType", filter.businessType()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.betweenIfPresent("receiptDate", filter.startDate(), filter.endDate()));
        return pageEntities(query, spec).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> search(String keyword, int maxSize) {
        Specification<Receipt> spec = combineSpecifications(
                VISIBILITY_POLICY.applyDeletedVisibility(null, false),
                Specs.keywordLike(keyword, RECEIPT_SEARCH_FIELDS)
        );
        return receiptRepository.findAll(spec, PageRequest.of(0, maxSize))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReceiptResponse detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    @Transactional
    public ReceiptResponse create(ReceiptRequest request) {
        ReceiptResponse created = createReceipt(
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Transactional
    public ReceiptResponse update(Long id, ReceiptRequest request) {
        ReceiptResponse updated = updateReceipt(id,
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
    }

    @Transactional
    public ReceiptResponse updateStatus(Long id, String status) {
        lockReceiptRoot(id);
        return doUpdateStatus(id, status);
    }

    @Transactional
    public void delete(Long id) {
        lockReceiptRoot(id);
        Receipt entity = requireEntity(id);
        STATUS_GUARD.assertDeleteAllowed(entity);
        beforeDelete(entity);
        entity.setDeletedFlag(true);
        saveEntity(entity);
        log.info("{} deleted: id={}", entity.getClass().getSimpleName(), id);
    }

    /**
     * 基类 create 的显式内联：雪花 ID → 归一化 → 校验 → 应用 → 终态双写守卫 → 保存。
     */
    private ReceiptResponse createReceipt(ReceiptRequest request) {
        Receipt entity = newEntity();
        long entityId = idGenerator.nextId();
        assignId(entity, entityId);
        ReceiptRequest normalized = normalizeCreateRequest(request, entityId);
        validateCreate(normalized);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        ReceiptResponse response = toSavedResponse(saveCreatedEntity(entity, normalized));
        log.info("{} created: id={}", entity.getClass().getSimpleName(), entityId);
        return response;
    }

    /**
     * 基类 update 的显式内联，状态断言序列逐字保持：
     * 编辑状态守卫 → 更新校验 → 快照当前状态 → 应用请求 →
     * assertRequestStatusTransitionAllowed → allowRequestToWriteFinalStatus 分支下的
     * assertRequestDidNotWriteFinalStatus → 保存。
     */
    private ReceiptResponse updateReceipt(Long id, ReceiptRequest request) {
        Receipt entity = requireEntity(id);
        ReceiptRequest normalized = normalizeUpdateRequest(entity, request);
        // 基类 allowProtectedStatusUpdate 默认 false：受保护状态单据不允许普通编辑。
        STATUS_GUARD.assertEditAllowed(entity, false);
        validateUpdate(entity, normalized);
        Optional<String> currentStatus = STATUS_GUARD.resolveStatus(entity);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestStatusTransitionAllowed(entity, currentStatus, allowedStatusTransitions());
        // 基类 allowRequestToWriteFinalStatus 默认 false：普通保存一律拒绝终态写入。
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        ReceiptResponse response = toSavedResponse(saveUpdatedEntity(entity, normalized));
        log.info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    /**
     * 基类 updateStatus 的显式内联：等值短路 → 迁移表校验 → beforeStatusUpdate → 写状态 → 状态保存。
     */
    private ReceiptResponse doUpdateStatus(Long id, String status) {
        Receipt entity = requireEntity(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toSavedResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(allowedStatusTransitions(), currentStatus, nextStatus);
        beforeStatusUpdate(entity, currentStatus, nextStatus);
        STATUS_GUARD.writeStatus(entity, nextStatus);
        ReceiptResponse response = toSavedResponse(saveEntity(entity));
        log.info(
                "{} status updated: id={}, {} -> {}",
                entity.getClass().getSimpleName(),
                id,
                currentStatus,
                nextStatus
        );
        return response;
    }

    protected void validateCreate(ReceiptRequest request) {
        ensureReceiptNoUnique(request.receiptNo());
    }

    protected void validateUpdate(Receipt entity, ReceiptRequest request) {
        if (StatusConstants.AUDITED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核收款单禁止修改");
        }
        if (!entity.getReceiptNo().equals(request.receiptNo())) {
            ensureReceiptNoUnique(request.receiptNo());
        }
    }

    protected ReceiptRequest normalizeCreateRequest(ReceiptRequest request, long entityId) {
        return new ReceiptRequest(
                resolveCreateBusinessNo(entityId),
                request.counterpartyType(),
                request.counterpartyId(),
                request.counterpartyCode(),
                request.counterpartyName(),
                request.receiptPurpose(),
                request.customerId(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.accountId(),
                request.sourceStatementId(),
                request.receiptDate(),
                request.payType(),
                request.amount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    private ReceiptRequest withStatus(ReceiptRequest request, String status) {
        return new ReceiptRequest(
                request.receiptNo(),
                request.counterpartyType(),
                request.counterpartyId(),
                request.counterpartyCode(),
                request.counterpartyName(),
                request.receiptPurpose(),
                request.customerId(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.accountId(),
                request.sourceCustomerStatementId(),
                request.receiptDate(),
                request.payType(),
                request.amount(),
                status,
                request.operatorName(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    protected ReceiptRequest normalizeUpdateRequest(Receipt entity, ReceiptRequest request) {
        return new ReceiptRequest(
                entity.getReceiptNo(),
                request.counterpartyType(),
                request.counterpartyId(),
                request.counterpartyCode(),
                request.counterpartyName(),
                request.receiptPurpose(),
                request.customerId(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.accountId(),
                request.sourceStatementId(),
                request.receiptDate(),
                request.payType(),
                request.amount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    protected Receipt newEntity() {
        return new Receipt();
    }

    protected void assignId(Receipt entity, Long id) {
        entity.setId(id);
    }

    protected Optional<Receipt> findActiveEntity(Long id) {
        return receiptRepository.findByIdAndDeletedFlagFalse(id);
    }

    protected Optional<Receipt> findVisibleEntity(Long id) {
        return receiptRepository.findById(id);
    }

    protected String notFoundMessage() {
        return "收款单不存在";
    }

    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    protected Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.DRAFT_TO_AUDITED_TRANSITIONS;
    }

    protected void beforeStatusUpdate(Receipt entity, String currentStatus, String nextStatus) {
        if (StatusConstants.AUDITED.equals(currentStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核收款单禁止反审核");
        }
        if (ReceiptPurposes.isSupplierReceipt(entity.getReceiptPurpose())) {
            if (entity.getCounterpartyId() == null || entity.getSettlementCompanyId() == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商收款缺少供应商或结算主体身份");
            }
            supplierPrepaymentBalanceService.validateSupplierReceipt(entity, nextStatus);
            return;
        }
        lockAllocationStatements(entity, null);
        settlementSyncService.captureOriginalAllocationStatementIds(entity);
        receiptAllocationService.validateExistingAllocationsForSettlement(entity, nextStatus);
    }

    protected void beforeDelete(Receipt entity) {
        if (StatusConstants.AUDITED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核收款单禁止删除");
        }
        if (StatusConstants.LEGACY_RECEIVED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "历史已收款单据仅供查询，不允许删除");
        }
        lockAllocationStatements(entity, null);
    }

    protected ReceiptResponse toDetailResponse(Receipt entity) {
        ReceiptResponse response = receiptMapper.toResponse(entity);
        return new ReceiptResponse(
                response.id(),
                response.receiptNo(),
                response.counterpartyType(),
                response.counterpartyId(),
                response.counterpartyCode(),
                response.counterpartyName(),
                response.receiptPurpose(),
                response.customerId(),
                response.customerCode(),
                response.customerName(),
                response.projectId(),
                response.projectName(),
                response.settlementCompanyId(),
                response.settlementCompanyName(),
                response.accountId(),
                response.sourceStatementId(),
                response.receiptDate(),
                response.payType(),
                response.amount(),
                response.status(),
                response.deletedFlag(),
                response.operatorName(),
                response.remark(),
                allocationResponseAssembler.toResponses(entity)
        );
    }

    protected ReceiptResponse toSavedResponse(Receipt entity) {
        return toDetailResponse(entity);
    }

    protected void apply(Receipt entity, ReceiptRequest request) {
        lockAllocationStatements(entity, request);
        applyService.apply(entity, request, this::nextId);
    }

    protected Receipt saveEntity(Receipt entity) {
        Receipt saved = receiptRepository.save(entity);
        settlementSyncService.syncCustomerStatements(saved);
        return saved;
    }

    protected Receipt saveCreatedEntity(Receipt entity, ReceiptRequest request) {
        return saveEntity(entity);
    }

    protected Receipt saveUpdatedEntity(Receipt entity, ReceiptRequest request) {
        return saveEntity(entity);
    }

    protected ReceiptResponse toResponse(Receipt entity) {
        return receiptMapper.toResponse(entity);
    }

    private void lockAllocationStatements(Receipt entity, ReceiptRequest request) {
        TreeSet<Long> customerStatementIds = new TreeSet<>();
        if (entity != null && !ReceiptPurposes.isSupplierReceipt(entity.getReceiptPurpose())) {
            customerStatementIds.addAll(existingAllocationStatementIds(entity));
        }
        if (request != null && !ReceiptPurposes.isSupplierReceipt(request.receiptPurpose())) {
            customerStatementIds.addAll(requestedAllocationStatementIds(request));
        }
        sourceAllocationLockService.lockStatementSources(
                List.copyOf(customerStatementIds),
                List.of()
        );
    }

    private List<Long> existingAllocationStatementIds(Receipt entity) {
        if (entity.getItems() != null && !entity.getItems().isEmpty()) {
            return entity.getItems().stream()
                    .map(ReceiptAllocation::getSourceStatementId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return entity.getSourceStatementId() == null
                ? List.of()
                : List.of(entity.getSourceStatementId());
    }

    private List<Long> requestedAllocationStatementIds(ReceiptRequest request) {
        if (request.items() != null && !request.items().isEmpty()) {
            return request.items().stream()
                    .map(ReceiptAllocationRequest::sourceStatementId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return request.sourceStatementId() == null
                ? List.of()
                : List.of(request.sourceStatementId());
    }

    private Receipt requireEntity(Long id) {
        return findActiveEntity(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    private Receipt requireDetailEntity(Long id) {
        if (allowViewingDeletedRecords()) {
            return findVisibleEntity(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        }
        return requireEntity(id);
    }

    private Page<Receipt> pageEntities(PageQuery query, Specification<Receipt> specification) {
        Specification<Receipt> effectiveSpec =
                VISIBILITY_POLICY.applyDeletedVisibility(specification, allowViewingDeletedRecords());
        return receiptRepository.findAll(effectiveSpec, query.toPageable("id"));
    }

    private Specification<Receipt> combineSpecifications(Specification<Receipt> left,
                                                         Specification<Receipt> right) {
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

    private void ensureReceiptNoUnique(String receiptNo) {
        if (receiptRepository.existsByReceiptNoAndDeletedFlagFalse(receiptNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "收款单号已存在");
        }
    }

    private void lockReceiptRoot(Long id) {
        receiptRepository.findByIdAndDeletedFlagFalseForUpdate(id);
    }
}
