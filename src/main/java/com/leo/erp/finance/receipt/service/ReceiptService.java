package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptPurposes;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import com.leo.erp.finance.receipt.mapper.ReceiptMapper;
import com.leo.erp.finance.receipt.repository.ReceiptRepository;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import com.leo.erp.finance.common.service.SupplierPrepaymentBalanceService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

@Service
public class ReceiptService extends AbstractCrudService<Receipt, ReceiptRequest, ReceiptResponse> {

    private final ReceiptRepository receiptRepository;
    private final ReceiptMapper receiptMapper;
    private final ReceiptApplyService applyService;
    private final ReceiptAllocationService receiptAllocationService;
    private final ReceiptAllocationResponseAssembler allocationResponseAssembler;
    private final ReceiptSettlementSyncService settlementSyncService;
    private final SourceAllocationLockService sourceAllocationLockService;
    private SupplierPrepaymentBalanceService supplierPrepaymentBalanceService;

    @Autowired
    public ReceiptService(ReceiptRepository receiptRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          ReceiptMapper receiptMapper,
                          ReceiptApplyService applyService,
                          ReceiptAllocationService receiptAllocationService,
                          ReceiptAllocationResponseAssembler allocationResponseAssembler,
                          ReceiptSettlementSyncService settlementSyncService,
                          SourceAllocationLockService sourceAllocationLockService) {
        super(snowflakeIdGenerator);
        this.receiptRepository = receiptRepository;
        this.receiptMapper = receiptMapper;
        this.applyService = applyService;
        this.receiptAllocationService = receiptAllocationService;
        this.allocationResponseAssembler = allocationResponseAssembler;
        this.settlementSyncService = settlementSyncService;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    @Autowired(required = false)
    void setSupplierPrepaymentBalanceService(SupplierPrepaymentBalanceService supplierPrepaymentBalanceService) {
        this.supplierPrepaymentBalanceService = supplierPrepaymentBalanceService;
    }

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
        return page(query, spec, receiptRepository);
    }

    private static final String[] RECEIPT_SEARCH_FIELDS = {
            "receiptNo",
            "customerName",
            "projectName"
    };

    public List<ReceiptResponse> search(String keyword, int maxSize) {
        return search(keyword, RECEIPT_SEARCH_FIELDS, maxSize, null, receiptRepository);
    }

    @Transactional
    public ReceiptResponse createAndAudit(ReceiptRequest request) {
        ReceiptResponse created = create(withStatus(request, StatusConstants.DRAFT));
        return updateStatus(created.id(), StatusConstants.AUDITED);
    }

    @Transactional
    public ReceiptResponse updateAndAudit(Long id, ReceiptRequest request) {
        update(id, withStatus(request, StatusConstants.DRAFT));
        return updateStatus(id, StatusConstants.AUDITED);
    }

    @Override
    @Transactional
    public ReceiptResponse updateStatus(Long id, String status) {
        lockReceiptRoot(id);
        return super.updateStatus(id, status);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        lockReceiptRoot(id);
        super.delete(id);
    }

    @Override
    protected void validateCreate(ReceiptRequest request) {
        ensureReceiptNoUnique(request.receiptNo());
    }

    @Override
    protected void validateUpdate(Receipt entity, ReceiptRequest request) {
        if (StatusConstants.AUDITED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核收款单禁止修改");
        }
        if (!entity.getReceiptNo().equals(request.receiptNo())) {
            ensureReceiptNoUnique(request.receiptNo());
        }
    }

    @Override
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
                request.sourceStatementId(),
                request.receiptDate(),
                request.payType(),
                request.amount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items()
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
                request.sourceCustomerStatementId(),
                request.receiptDate(),
                request.payType(),
                request.amount(),
                status,
                request.operatorName(),
                request.remark(),
                request.items()
        );
    }

    @Override
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
                request.sourceStatementId(),
                request.receiptDate(),
                request.payType(),
                request.amount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected Receipt newEntity() {
        return new Receipt();
    }

    @Override
    protected void assignId(Receipt entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<Receipt> findActiveEntity(Long id) {
        return receiptRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<Receipt> findVisibleEntity(Long id) {
        return receiptRepository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "收款单不存在";
    }

    @Override
    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return java.util.Set.of(StatusConstants.DRAFT + "->" + StatusConstants.AUDITED);
    }

    @Override
    protected void beforeStatusUpdate(Receipt entity, String currentStatus, String nextStatus) {
        if (StatusConstants.AUDITED.equals(currentStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核收款单禁止反审核");
        }
        if (ReceiptPurposes.isSupplierReceipt(entity.getReceiptPurpose())) {
            if (entity.getCounterpartyId() == null || entity.getSettlementCompanyId() == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商收款缺少供应商或结算主体身份");
            }
            requireSupplierPrepaymentBalanceService().validateSupplierReceipt(entity, nextStatus);
            return;
        }
        lockAllocationStatements(entity, null);
        settlementSyncService.captureOriginalAllocationStatementIds(entity);
        receiptAllocationService.validateExistingAllocationsForSettlement(entity, nextStatus);
    }

    @Override
    protected void beforeDelete(Receipt entity) {
        if (StatusConstants.AUDITED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核收款单禁止删除");
        }
        lockAllocationStatements(entity, null);
    }

    @Override
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

    @Override
    protected ReceiptResponse toSavedResponse(Receipt entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void apply(Receipt entity, ReceiptRequest request) {
        lockAllocationStatements(entity, request);
        applyService.apply(entity, request, this::nextId);
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
                List.of(),
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

    @Override
    protected Receipt saveEntity(Receipt entity) {
        Receipt saved = receiptRepository.save(entity);
        settlementSyncService.syncCustomerStatements(saved);
        return saved;
    }

    @Override
    protected ReceiptResponse toResponse(Receipt entity) {
        return receiptMapper.toResponse(entity);
    }

    private void ensureReceiptNoUnique(String receiptNo) {
        if (receiptRepository.existsByReceiptNoAndDeletedFlagFalse(receiptNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "收款单号已存在");
        }
    }

    private SupplierPrepaymentBalanceService requireSupplierPrepaymentBalanceService() {
        if (supplierPrepaymentBalanceService == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商预付款余额服务不可用");
        }
        return supplierPrepaymentBalanceService;
    }

    private void lockReceiptRoot(Long id) {
        receiptRepository.findByIdAndDeletedFlagFalseForUpdate(id);
    }

}
