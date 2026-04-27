package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.InvoiceAllocationSupport;
import com.leo.erp.common.support.InvoiceAllocationSupport.AllocationProgress;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceiptItem;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.finance.invoicereceipt.mapper.InvoiceReceiptMapper;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptItemRequest;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptItemResponse;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptRequest;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptResponse;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.service.CompanySettingService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InvoiceReceiptService extends AbstractCrudService<InvoiceReceipt, InvoiceReceiptRequest, InvoiceReceiptResponse> {

    private final InvoiceReceiptRepository repository;
    private final InvoiceReceiptMapper mapper;
    private final CompanySettingService companySettingService;
    private final PurchaseOrderItemQueryService purchaseOrderItemQueryService;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public InvoiceReceiptService(InvoiceReceiptRepository repository,
                                 SnowflakeIdGenerator idGenerator,
                                 InvoiceReceiptMapper mapper,
                                 CompanySettingService companySettingService,
                                 PurchaseOrderItemQueryService purchaseOrderItemQueryService,
                                 WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.mapper = mapper;
        this.companySettingService = companySettingService;
        this.purchaseOrderItemQueryService = purchaseOrderItemQueryService;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceReceiptResponse> page(PageQuery query,
                                             String keyword,
                                             String supplierName,
                                             String status,
                                             LocalDate startDate,
                                             LocalDate endDate) {
        Specification<InvoiceReceipt> spec = Specs.<InvoiceReceipt>notDeleted()
                .and(Specs.keywordLike(keyword, "receiveNo", "invoiceNo", "sourcePurchaseOrderNos", "supplierName"))
                .and(Specs.equalIfPresent("supplierName", supplierName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("invoiceDate", startDate, endDate));
        return page(query, spec, repository);
    }

    @Override
    protected InvoiceReceiptResponse toDetailResponse(InvoiceReceipt entity) {
        InvoiceReceiptResponse response = mapper.toResponse(entity);
        return new InvoiceReceiptResponse(
                response.id(),
                response.receiveNo(),
                response.invoiceNo(),
                response.sourcePurchaseOrderNos(),
                response.supplierName(),
                response.invoiceTitle(),
                response.invoiceDate(),
                response.invoiceType(),
                response.amount(),
                response.taxAmount(),
                response.status(),
                response.operatorName(),
                response.remark(),
                entity.getItems().stream().map(this::toItemResponse).toList()
        );
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
    protected String notFoundMessage() {
        return "收票单不存在";
    }

    @Override
    protected void apply(InvoiceReceipt entity, InvoiceReceiptRequest request) {
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "invoice-receipts",
                entity.getStatus(),
                request.status(),
                "已收票"
        );
        entity.setReceiveNo(request.receiveNo());
        entity.setInvoiceNo(request.invoiceNo());
        entity.setSupplierName(request.supplierName());
        entity.setInvoiceTitle(request.invoiceTitle() == null || request.invoiceTitle().isBlank()
                ? request.supplierName()
                : request.invoiceTitle().trim());
        entity.setInvoiceDate(request.invoiceDate());
        entity.setInvoiceType(request.invoiceType());
        entity.setStatus(request.status());
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());

        List<Long> sourceItemIds = request.items().stream()
                .map(InvoiceReceiptItemRequest::sourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap = loadSourcePurchaseOrderItemMap(sourceItemIds);
        Map<Long, AllocationProgress> allocatedProgressMap = loadAllocatedProgressMap(sourceItemIds, entity.getId());
        Map<Long, AllocationProgress> requestProgressMap = new java.util.HashMap<>();
        LinkedHashSet<String> sourcePurchaseOrderNos = new LinkedHashSet<>();
        List<InvoiceReceiptItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                InvoiceReceiptItem::getId,
                InvoiceReceiptItemRequest::id,
                InvoiceReceiptItem::new,
                this::nextId,
                InvoiceReceiptItem::setId
        );
        BigDecimal amount = BigDecimal.ZERO;
        for (int i = 0; i < request.items().size(); i++) {
            InvoiceReceiptItemRequest source = request.items().get(i);
            ResolvedInvoiceReceiptItem resolvedItem = resolveItem(source);
            validateSourcePurchaseOrderAllocation(
                    source,
                    i + 1,
                    resolvedItem,
                    sourcePurchaseOrderItemMap,
                    allocatedProgressMap,
                    requestProgressMap
            );
            InvoiceReceiptItem item = items.get(i);
            item.setInvoiceReceipt(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(source.sourceNo());
            item.setSourcePurchaseOrderItemId(source.sourcePurchaseOrderItemId());
            item.setMaterialCode(source.materialCode());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setUnit(source.unit());
            item.setWarehouseName(source.warehouseName());
            item.setBatchNo(source.batchNo());
            item.setQuantity(source.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
            item.setPieceWeightTon(source.pieceWeightTon());
            item.setPiecesPerBundle(source.piecesPerBundle());
            item.setWeightTon(resolvedItem.weightTon());
            item.setUnitPrice(source.unitPrice());
            BigDecimal lineAmount = resolvedItem.amount();
            item.setAmount(lineAmount);
            sourcePurchaseOrderNos.add(source.sourceNo());
            amount = amount.add(lineAmount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(InvoiceReceiptItem::getLineNo));
        entity.setSourcePurchaseOrderNos(String.join(", ", sourcePurchaseOrderNos));
        entity.setAmount(amount);
        InvoiceAllocationSupport.validateDeclaredAmount("收票", request.amount(), amount);
        entity.setTaxAmount(InvoiceAllocationSupport.calculateTaxAmount(amount, request.taxAmount(), companySettingService));
    }

    @Override
    protected InvoiceReceipt saveEntity(InvoiceReceipt entity) {
        return repository.save(entity);
    }

    @Override
    protected InvoiceReceiptResponse toResponse(InvoiceReceipt entity) {
        return mapper.toResponse(entity);
    }

    private Map<Long, PurchaseOrderItem> loadSourcePurchaseOrderItemMap(List<Long> sourceItemIds) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }
        return purchaseOrderItemQueryService.findActiveByIdIn(sourceItemIds).stream()
                .collect(java.util.HashMap::new, (map, item) -> map.put(item.getId(), item), java.util.HashMap::putAll);
    }

    private Map<Long, AllocationProgress> loadAllocatedProgressMap(List<Long> sourceItemIds, Long currentReceiptId) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }
        return repository.summarizeAllocatedBySourcePurchaseOrderItemIds(sourceItemIds, currentReceiptId).stream()
                .collect(java.util.HashMap::new, (map, summary) -> map.put(
                        summary.getSourcePurchaseOrderItemId(),
                        new AllocationProgress(TradeItemCalculator.safeBigDecimal(summary.getTotalWeightTon()), TradeItemCalculator.safeBigDecimal(summary.getTotalAmount()))
                ), java.util.HashMap::putAll);
    }

    private void validateSourcePurchaseOrderAllocation(InvoiceReceiptItemRequest source,
                                                       int lineNo,
                                                       ResolvedInvoiceReceiptItem resolvedItem,
                                                       Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
                                                       Map<Long, AllocationProgress> allocatedProgressMap,
                                                       Map<Long, AllocationProgress> requestProgressMap) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不能为空");
        }

        PurchaseOrderItem sourcePurchaseOrderItem = sourcePurchaseOrderItemMap.get(sourcePurchaseOrderItemId);
        if (sourcePurchaseOrderItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不存在");
        }

        AllocationProgress allocatedProgress = allocatedProgressMap.getOrDefault(
                sourcePurchaseOrderItemId,
                AllocationProgress.EMPTY
        );
        AllocationProgress requestProgress = requestProgressMap.getOrDefault(
                sourcePurchaseOrderItemId,
                AllocationProgress.EMPTY
        );
        BigDecimal nextWeightTon = allocatedProgress.weightTon()
                .add(requestProgress.weightTon())
                .add(resolvedItem.weightTon());
        if (nextWeightTon.compareTo(TradeItemCalculator.safeBigDecimal(sourcePurchaseOrderItem.getWeightTon())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细可收票吨位不足");
        }

        BigDecimal nextAmount = allocatedProgress.amount()
                .add(requestProgress.amount())
                .add(resolvedItem.amount());
        if (nextAmount.compareTo(TradeItemCalculator.safeBigDecimal(sourcePurchaseOrderItem.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细可收票金额不足");
        }

        requestProgressMap.merge(
                sourcePurchaseOrderItemId,
                new AllocationProgress(resolvedItem.weightTon(), resolvedItem.amount()),
                AllocationProgress::merge
        );
    }

    private ResolvedInvoiceReceiptItem resolveItem(InvoiceReceiptItemRequest source) {
        BigDecimal weightTon = InvoiceAllocationSupport.resolveWeightTon(source.quantity(), source.pieceWeightTon(), source.weightTon());
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.unitPrice());
        return new ResolvedInvoiceReceiptItem(weightTon, amount);
    }

    private InvoiceReceiptItemResponse toItemResponse(InvoiceReceiptItem item) {
        return new InvoiceReceiptItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceNo(),
                item.getSourcePurchaseOrderItemId(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getWarehouseName(),
                item.getBatchNo(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                item.getUnitPrice(),
                item.getAmount()
        );
    }

    private record ResolvedInvoiceReceiptItem(
            BigDecimal weightTon,
            BigDecimal amount
    ) {
    }
}
