package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.mapper.ReceiptMapper;
import com.leo.erp.finance.receipt.repository.ReceiptRepository;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ReceiptService extends AbstractCrudService<Receipt, ReceiptRequest, ReceiptResponse> {

    private static final String RECEIPT_STATUS_SETTLED = StatusConstants.RECEIVED;

    private final ReceiptRepository receiptRepository;
    private final ReceiptMapper receiptMapper;
    private final ReceiptApplyService applyService;
    private final ReceiptAllocationService receiptAllocationService;
    private final ReceiptAllocationResponseAssembler allocationResponseAssembler;
    private final ReceiptSettlementSyncService settlementSyncService;

    @Autowired
    public ReceiptService(ReceiptRepository receiptRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          ReceiptMapper receiptMapper,
                          ReceiptApplyService applyService,
                          ReceiptAllocationService receiptAllocationService,
                          ReceiptAllocationResponseAssembler allocationResponseAssembler,
                          ReceiptSettlementSyncService settlementSyncService) {
        super(snowflakeIdGenerator);
        this.receiptRepository = receiptRepository;
        this.receiptMapper = receiptMapper;
        this.applyService = applyService;
        this.receiptAllocationService = receiptAllocationService;
        this.allocationResponseAssembler = allocationResponseAssembler;
        this.settlementSyncService = settlementSyncService;
    }

    public Page<ReceiptResponse> page(PageQuery query, PageFilter filter) {
        Specification<Receipt> spec = Specs.<Receipt>keywordLike(filter.keyword(), "receiptNo", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
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

    @Override
    protected void validateCreate(ReceiptRequest request) {
        ensureReceiptNoUnique(request.receiptNo());
    }

    @Override
    protected void validateUpdate(Receipt entity, ReceiptRequest request) {
        if (!entity.getReceiptNo().equals(request.receiptNo())) {
            ensureReceiptNoUnique(request.receiptNo());
        }
    }

    @Override
    protected ReceiptRequest normalizeCreateRequest(ReceiptRequest request, long entityId) {
        return new ReceiptRequest(
                resolveCreateBusinessNo("receipt", request.receiptNo(), entityId),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
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
    protected ReceiptRequest normalizeUpdateRequest(Receipt entity, ReceiptRequest request) {
        return new ReceiptRequest(
                entity.getReceiptNo(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
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
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return java.util.Set.of(
                StatusConstants.DRAFT + "->" + StatusConstants.RECEIVED,
                StatusConstants.RECEIVED + "->" + StatusConstants.DRAFT
        );
    }

    @Override
    protected void beforeStatusUpdate(Receipt entity, String currentStatus, String nextStatus) {
        settlementSyncService.captureOriginalAllocationStatementIds(entity);
        receiptAllocationService.validateExistingAllocationsForSettlement(entity, nextStatus);
    }

    @Override
    protected ReceiptResponse toDetailResponse(Receipt entity) {
        ReceiptResponse response = receiptMapper.toResponse(entity);
        return new ReceiptResponse(
                response.id(),
                response.receiptNo(),
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
        applyService.apply(entity, request, this::nextId);
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

}
