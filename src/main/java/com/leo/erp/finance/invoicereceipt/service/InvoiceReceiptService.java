package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptRequest;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

@Service
public class InvoiceReceiptService extends AbstractCrudService<InvoiceReceipt, InvoiceReceiptRequest, InvoiceReceiptResponse> {

    private final InvoiceReceiptRepository repository;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final InvoiceReceiptApplyService applyService;
    private final InvoiceReceiptSourceService invoiceReceiptSourceService;
    private final InvoiceReceiptResponseAssembler responseAssembler;

    public InvoiceReceiptService(InvoiceReceiptRepository repository,
                                 SnowflakeIdGenerator idGenerator,
                                 SourceAllocationLockService sourceAllocationLockService,
                                 InvoiceReceiptApplyService applyService,
                                 InvoiceReceiptSourceService invoiceReceiptSourceService,
                                 InvoiceReceiptResponseAssembler responseAssembler) {
        super(idGenerator);
        this.repository = repository;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.applyService = applyService;
        this.invoiceReceiptSourceService = invoiceReceiptSourceService;
        this.responseAssembler = responseAssembler;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceReceiptResponse> page(PageQuery query, PageFilter filter) {
        Specification<InvoiceReceipt> spec = Specs.<InvoiceReceipt>keywordLike(filter.keyword(), "receiveNo", "invoiceNo", "supplierName")
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("invoiceDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    private static final String[] INVOICE_RECEIPT_SEARCH_FIELDS = {
            "receiveNo",
            "invoiceNo",
            "supplierName"
    };

    @Transactional(readOnly = true)
    public List<InvoiceReceiptResponse> search(String keyword, int maxSize) {
        return search(keyword, INVOICE_RECEIPT_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected InvoiceReceiptResponse toDetailResponse(InvoiceReceipt entity) {
        return responseAssembler.toDetailResponse(entity);
    }

    @Override
    protected InvoiceReceiptResponse toSavedResponse(InvoiceReceipt entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void validateCreate(InvoiceReceiptRequest request) {
        if (repository.existsByReceiveNoAndDeletedFlagFalse(request.receiveNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "收票单号已存在");
        }
    }

    @Override
    protected void validateUpdate(InvoiceReceipt entity, InvoiceReceiptRequest request) {
        if (!entity.getReceiveNo().equals(request.receiveNo())
                && repository.existsByReceiveNoAndDeletedFlagFalse(request.receiveNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "收票单号已存在");
        }
    }

    @Override
    protected InvoiceReceiptRequest normalizeCreateRequest(InvoiceReceiptRequest request, long entityId) {
        return new InvoiceReceiptRequest(
                resolveCreateBusinessNo("invoice-receipt", request.receiveNo(), entityId),
                request.invoiceNo(),
                request.supplierCode(),
                request.supplierName(),
                request.invoiceTitle(),
                request.invoiceDate(),
                request.invoiceType(),
                request.amount(),
                request.taxAmount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items(),
                request.supplierId()
        );
    }

    @Override
    protected InvoiceReceiptRequest normalizeUpdateRequest(InvoiceReceipt entity, InvoiceReceiptRequest request) {
        return new InvoiceReceiptRequest(
                entity.getReceiveNo(),
                request.invoiceNo(),
                request.supplierCode() == null || request.supplierCode().isBlank()
                        ? entity.getSupplierCode()
                        : request.supplierCode(),
                request.supplierName(),
                request.invoiceTitle(),
                request.invoiceDate(),
                request.invoiceType(),
                request.amount(),
                request.taxAmount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items(),
                request.supplierId()
        );
    }

    @Override
    protected InvoiceReceipt newEntity() {
        return new InvoiceReceipt();
    }

    @Override
    protected void assignId(InvoiceReceipt entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<InvoiceReceipt> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<InvoiceReceipt> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "收票单不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return java.util.Set.of(
                StatusConstants.DRAFT + "->" + StatusConstants.INVOICE_RECEIVED,
                StatusConstants.INVOICE_RECEIVED + "->" + StatusConstants.DRAFT
        );
    }

    @Override
    protected void beforeStatusUpdate(InvoiceReceipt entity, String currentStatus, String nextStatus) {
        lockSourceItems(entity, null);
        if (!StatusConstants.INVOICE_RECEIVED.equals(nextStatus)) {
            return;
        }
        invoiceReceiptSourceService.validateExistingItemsForReceipt(entity);
    }

    @Override
    protected void apply(InvoiceReceipt entity, InvoiceReceiptRequest request) {
        lockSourceItems(entity, request);
        applyService.apply(entity, request, this::nextId);
    }

    @Override
    protected void beforeDelete(InvoiceReceipt entity) {
        lockSourceItems(entity, null);
    }

    @Override
    protected InvoiceReceipt saveEntity(InvoiceReceipt entity) {
        return repository.save(entity);
    }

    @Override
    protected InvoiceReceiptResponse toResponse(InvoiceReceipt entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

    private void lockSourceItems(InvoiceReceipt entity, InvoiceReceiptRequest request) {
        TreeSet<Long> sourceItemIds = new TreeSet<>();
        if (entity.getItems() != null) {
            entity.getItems().stream()
                    .map(item -> item.getSourcePurchaseOrderItemId())
                    .filter(id -> id != null)
                    .forEach(sourceItemIds::add);
        }
        if (request != null && request.items() != null) {
            request.items().stream()
                    .map(item -> item.sourcePurchaseOrderItemId())
                    .filter(id -> id != null)
                    .forEach(sourceItemIds::add);
        }
        sourceAllocationLockService.lockTradeItemSources(List.copyOf(sourceItemIds), List.of(), List.of());
    }

}
