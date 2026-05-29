package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.InvoiceAllocationSupport;
import com.leo.erp.common.support.InvoiceAllocationSupport.AllocationProgress;
import com.leo.erp.common.support.StatusConstants;
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
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.service.CompanySettingService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        InvoiceReceiptResponse response = mapper.toResponse(entity);
        return new InvoiceReceiptResponse(
                response.id(),
                response.receiveNo(),
                response.invoiceNo(),
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
    protected InvoiceReceiptRequest normalizeCreateRequest(InvoiceReceiptRequest request, long entityId) {
        return new InvoiceReceiptRequest(
                resolveCreateBusinessNo("invoice-receipt", request.receiveNo(), entityId),
                request.invoiceNo(),
                request.supplierName(),
                request.invoiceTitle(),
                request.invoiceDate(),
                request.invoiceType(),
                request.amount(),
                request.taxAmount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected InvoiceReceiptRequest normalizeUpdateRequest(InvoiceReceipt entity, InvoiceReceiptRequest request) {
        return new InvoiceReceiptRequest(
                entity.getReceiveNo(),
                request.invoiceNo(),
                request.supplierName(),
                request.invoiceTitle(),
                request.invoiceDate(),
                request.invoiceType(),
                request.amount(),
                request.taxAmount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items()
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
        if (!StatusConstants.INVOICE_RECEIVED.equals(nextStatus)) {
            return;
        }
        List<InvoiceReceiptItemRequest> items = entity.getItems().stream()
                .map(item -> new InvoiceReceiptItemRequest(
                        item.getId(),
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
                ))
                .toList();
        List<Long> sourceItemIds = extractSourceItemIds(items);
        validateSourcePurchaseOrderAllocations(
                items,
                loadSourcePurchaseOrderItemMap(sourceItemIds),
                loadAllocatedProgressMap(sourceItemIds, entity.getId())
        );
    }

    @Override
    protected void apply(InvoiceReceipt entity, InvoiceReceiptRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "收票单状态",
                StatusConstants.ALLOWED_INVOICE_RECEIPT_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "invoice-receipt",
                entity.getStatus(),
                nextStatus,
                StatusConstants.INVOICE_RECEIVED
        );
        entity.setReceiveNo(request.receiveNo());
        entity.setInvoiceNo(request.invoiceNo());
        entity.setSupplierName(request.supplierName());
        entity.setInvoiceTitle(request.invoiceTitle() == null || request.invoiceTitle().isBlank()
                ? request.supplierName()
                : request.invoiceTitle().trim());
        entity.setInvoiceDate(request.invoiceDate());
        entity.setInvoiceType(request.invoiceType());
        entity.setStatus(nextStatus);
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());

        List<Long> sourceItemIds = extractSourceItemIds(request.items());
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap = loadSourcePurchaseOrderItemMap(sourceItemIds);
        Map<Long, AllocationProgress> allocatedProgressMap = loadAllocatedProgressMap(sourceItemIds, entity.getId());
        Map<Long, AllocationProgress> requestProgressMap = new java.util.HashMap<>();
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
            ResolvedInvoiceReceiptItem resolvedItem = resolveItem(source, sourcePurchaseOrderItemMap, i + 1);
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
            item.setSourceNo(resolvedItem.sourceNo());
            item.setSourcePurchaseOrderItemId(source.sourcePurchaseOrderItemId());
            item.setMaterialCode(resolvedItem.materialCode());
            item.setBrand(resolvedItem.brand());
            item.setCategory(resolvedItem.category());
            item.setMaterial(resolvedItem.material());
            item.setSpec(resolvedItem.spec());
            item.setLength(resolvedItem.length());
            item.setUnit(resolvedItem.unit());
            item.setWarehouseName(resolvedItem.warehouseName());
            item.setBatchNo(resolvedItem.batchNo());
            item.setQuantity(source.quantity());
            item.setQuantityUnit(resolvedItem.quantityUnit());
            item.setPieceWeightTon(resolvedItem.pieceWeightTon());
            item.setPiecesPerBundle(resolvedItem.piecesPerBundle());
            item.setWeightTon(resolvedItem.weightTon());
            item.setUnitPrice(resolvedItem.unitPrice());
            BigDecimal lineAmount = resolvedItem.amount();
            item.setAmount(lineAmount);
            amount = amount.add(lineAmount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(InvoiceReceiptItem::getLineNo));
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

    private List<Long> extractSourceItemIds(List<InvoiceReceiptItemRequest> items) {
        return items.stream()
                .map(InvoiceReceiptItemRequest::sourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
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

    private void validateSourcePurchaseOrderAllocations(
            List<InvoiceReceiptItemRequest> items,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
            Map<Long, AllocationProgress> allocatedProgressMap
    ) {
        Map<Long, AllocationProgress> requestProgressMap = new java.util.HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            InvoiceReceiptItemRequest source = items.get(i);
            validateSourcePurchaseOrderAllocation(
                    source,
                    i + 1,
                    resolveItem(source, sourcePurchaseOrderItemMap, i + 1),
                    sourcePurchaseOrderItemMap,
                    allocatedProgressMap,
                    requestProgressMap
            );
        }
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

    private ResolvedInvoiceReceiptItem resolveItem(InvoiceReceiptItemRequest source,
                                                   Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
                                                   int lineNo) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不能为空");
        }
        PurchaseOrderItem sourcePurchaseOrderItem = sourcePurchaseOrderItemMap.get(sourcePurchaseOrderItemId);
        if (sourcePurchaseOrderItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不存在");
        }
        PurchaseOrder sourcePurchaseOrder = sourcePurchaseOrderItem.getPurchaseOrder();
        if (sourcePurchaseOrder == null || sourcePurchaseOrder.getOrderNo() == null || sourcePurchaseOrder.getOrderNo().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单不存在");
        }
        BigDecimal pieceWeightTon = TradeItemCalculator.scaleWeightTon(sourcePurchaseOrderItem.getPieceWeightTon());
        BigDecimal unitPrice = TradeItemCalculator.scaleAmount(sourcePurchaseOrderItem.getUnitPrice());
        BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), pieceWeightTon);
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, unitPrice);
        return new ResolvedInvoiceReceiptItem(
                sourcePurchaseOrder.getOrderNo(),
                sourcePurchaseOrderItem.getMaterialCode(),
                sourcePurchaseOrderItem.getBrand(),
                sourcePurchaseOrderItem.getCategory(),
                sourcePurchaseOrderItem.getMaterial(),
                sourcePurchaseOrderItem.getSpec(),
                sourcePurchaseOrderItem.getLength(),
                sourcePurchaseOrderItem.getUnit(),
                sourcePurchaseOrderItem.getWarehouseName(),
                sourcePurchaseOrderItem.getBatchNo(),
                TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()),
                pieceWeightTon,
                sourcePurchaseOrderItem.getPiecesPerBundle(),
                unitPrice,
                weightTon,
                amount
        );
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
            String sourceNo,
            String materialCode,
            String brand,
            String category,
            String material,
            String spec,
            String length,
            String unit,
            String warehouseName,
            String batchNo,
            String quantityUnit,
            BigDecimal pieceWeightTon,
            Integer piecesPerBundle,
            BigDecimal unitPrice,
            BigDecimal weightTon,
            BigDecimal amount
    ) {
    }
}
