package com.leo.erp.finance.invoiceissue.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.InvoiceAllocationSupport;
import com.leo.erp.common.support.InvoiceAllocationSupport.AllocationProgress;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssueItem;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.finance.invoiceissue.mapper.InvoiceIssueMapper;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueItemRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueItemResponse;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueResponse;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.system.company.service.CompanySettingService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InvoiceIssueService extends AbstractCrudService<InvoiceIssue, InvoiceIssueRequest, InvoiceIssueResponse> {

    private final InvoiceIssueRepository repository;
    private final SalesOrderItemQueryService salesOrderItemQueryService;
    private final InvoiceIssueMapper mapper;
    private final CompanySettingService companySettingService;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public InvoiceIssueService(InvoiceIssueRepository repository,
                               SalesOrderItemQueryService salesOrderItemQueryService,
                               SnowflakeIdGenerator idGenerator,
                               InvoiceIssueMapper mapper,
                               CompanySettingService companySettingService,
                               WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.salesOrderItemQueryService = salesOrderItemQueryService;
        this.mapper = mapper;
        this.companySettingService = companySettingService;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceIssueResponse> page(PageQuery query,
                                           String keyword,
                                           String customerName,
                                           String status,
                                           LocalDate startDate,
                                           LocalDate endDate) {
        Specification<InvoiceIssue> spec = Specs.<InvoiceIssue>keywordLike(keyword, "issueNo", "invoiceNo", "sourceSalesOrderNos", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", customerName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("invoiceDate", startDate, endDate));
        return page(query, spec, repository);
    }

    private static final String[] INVOICE_ISSUE_SEARCH_FIELDS = {
            "issueNo",
            "invoiceNo",
            "sourceSalesOrderNos",
            "customerName",
            "projectName"
    };

    @Transactional(readOnly = true)
    public List<InvoiceIssueResponse> search(String keyword, int maxSize) {
        return search(keyword, INVOICE_ISSUE_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected InvoiceIssueResponse toDetailResponse(InvoiceIssue entity) {
        InvoiceIssueResponse response = mapper.toResponse(entity);
        return new InvoiceIssueResponse(
                response.id(),
                response.issueNo(),
                response.invoiceNo(),
                response.sourceSalesOrderNos(),
                response.customerName(),
                response.projectName(),
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
    protected InvoiceIssueResponse toSavedResponse(InvoiceIssue entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void validateCreate(InvoiceIssueRequest request) {
        if (repository.existsByIssueNoAndDeletedFlagFalse(request.issueNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "开票单号已存在");
        }
    }

    @Override
    protected void validateUpdate(InvoiceIssue entity, InvoiceIssueRequest request) {
        if (!entity.getIssueNo().equals(request.issueNo())
                && repository.existsByIssueNoAndDeletedFlagFalse(request.issueNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "开票单号已存在");
        }
    }

    @Override
    protected InvoiceIssueRequest normalizeCreateRequest(InvoiceIssueRequest request, long entityId) {
        return new InvoiceIssueRequest(
                resolveCreateBusinessNo("invoice-issue", request.issueNo(), entityId),
                request.invoiceNo(),
                request.sourceSalesOrderNos(),
                request.customerName(),
                request.projectName(),
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
    protected InvoiceIssueRequest normalizeUpdateRequest(InvoiceIssue entity, InvoiceIssueRequest request) {
        return new InvoiceIssueRequest(
                entity.getIssueNo(),
                request.invoiceNo(),
                request.sourceSalesOrderNos(),
                request.customerName(),
                request.projectName(),
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
    protected InvoiceIssue newEntity() {
        return new InvoiceIssue();
    }

    @Override
    protected void assignId(InvoiceIssue entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<InvoiceIssue> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<InvoiceIssue> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "开票单不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected void apply(InvoiceIssue entity, InvoiceIssueRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "开票单状态",
                StatusConstants.ALLOWED_INVOICE_ISSUE_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "invoice-issue",
                entity.getStatus(),
                nextStatus,
                StatusConstants.ISSUED
        );
        entity.setIssueNo(request.issueNo());
        entity.setInvoiceNo(request.invoiceNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setInvoiceDate(request.invoiceDate());
        entity.setInvoiceType(request.invoiceType());
        entity.setStatus(nextStatus);
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());

        List<Long> sourceItemIds = request.items().stream()
                .map(InvoiceIssueItemRequest::sourceSalesOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, SalesOrderItem> sourceSalesOrderItemMap = loadSourceSalesOrderItemMap(sourceItemIds);
        Map<Long, AllocationProgress> allocatedProgressMap = loadAllocatedProgressMap(sourceItemIds, entity.getId());
        Map<Long, AllocationProgress> requestProgressMap = new HashMap<>();
        LinkedHashSet<String> sourceSalesOrderNos = new LinkedHashSet<>();
        List<InvoiceIssueItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                InvoiceIssueItem::getId,
                InvoiceIssueItemRequest::id,
                InvoiceIssueItem::new,
                this::nextId,
                InvoiceIssueItem::setId
        );
        BigDecimal amount = BigDecimal.ZERO;
        for (int i = 0; i < request.items().size(); i++) {
            InvoiceIssueItemRequest source = request.items().get(i);
            ResolvedInvoiceIssueItem resolvedItem = resolveItem(source, sourceSalesOrderItemMap, i + 1);
            validateSourceSalesOrderAllocation(
                    source,
                    i + 1,
                    resolvedItem,
                    sourceSalesOrderItemMap,
                    allocatedProgressMap,
                    requestProgressMap
            );
            InvoiceIssueItem item = items.get(i);
            item.setInvoiceIssue(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(resolvedItem.sourceNo());
            item.setSourceSalesOrderItemId(source.sourceSalesOrderItemId());
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
            sourceSalesOrderNos.add(resolvedItem.sourceNo());
            amount = amount.add(lineAmount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(InvoiceIssueItem::getLineNo));
        entity.setSourceSalesOrderNos(String.join(", ", sourceSalesOrderNos));
        entity.setAmount(amount);
        InvoiceAllocationSupport.validateDeclaredAmount("开票", request.amount(), amount);
        entity.setTaxAmount(InvoiceAllocationSupport.calculateTaxAmount(amount, request.taxAmount(), companySettingService));
    }

    @Override
    protected InvoiceIssue saveEntity(InvoiceIssue entity) {
        return repository.save(entity);
    }

    @Override
    protected InvoiceIssueResponse toResponse(InvoiceIssue entity) {
        return mapper.toResponse(entity);
    }

    private Map<Long, SalesOrderItem> loadSourceSalesOrderItemMap(List<Long> sourceItemIds) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }

        return salesOrderItemQueryService.findActiveByIdIn(sourceItemIds).stream()
                .collect(HashMap::new, (map, item) -> map.put(item.getId(), item), HashMap::putAll);
    }

    private Map<Long, AllocationProgress> loadAllocatedProgressMap(List<Long> sourceItemIds, Long currentIssueId) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }
        return repository.summarizeAllocatedBySourceSalesOrderItemIds(
                        sourceItemIds,
                        currentIssueId
                ).stream()
                .collect(HashMap::new, (map, summary) -> map.put(
                        summary.getSourceSalesOrderItemId(),
                        new AllocationProgress(TradeItemCalculator.safeBigDecimal(summary.getTotalWeightTon()), TradeItemCalculator.safeBigDecimal(summary.getTotalAmount()))
                ), HashMap::putAll);
    }

    private void validateSourceSalesOrderAllocation(InvoiceIssueItemRequest source,
                                                    int lineNo,
                                                    ResolvedInvoiceIssueItem resolvedItem,
                                                    Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                                    Map<Long, AllocationProgress> allocatedProgressMap,
                                                    Map<Long, AllocationProgress> requestProgressMap) {
        Long sourceSalesOrderItemId = source.sourceSalesOrderItemId();
        if (sourceSalesOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不能为空");
        }

        SalesOrderItem sourceSalesOrderItem = sourceSalesOrderItemMap.get(sourceSalesOrderItemId);
        if (sourceSalesOrderItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不存在");
        }

        AllocationProgress allocatedProgress = allocatedProgressMap.getOrDefault(
                sourceSalesOrderItemId,
                AllocationProgress.EMPTY
        );
        AllocationProgress requestProgress = requestProgressMap.getOrDefault(
                sourceSalesOrderItemId,
                AllocationProgress.EMPTY
        );
        BigDecimal nextWeightTon = allocatedProgress.weightTon()
                .add(requestProgress.weightTon())
                .add(resolvedItem.weightTon());
        if (nextWeightTon.compareTo(TradeItemCalculator.safeBigDecimal(sourceSalesOrderItem.getWeightTon())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细可开票吨位不足");
        }

        BigDecimal nextAmount = allocatedProgress.amount()
                .add(requestProgress.amount())
                .add(resolvedItem.amount());
        if (nextAmount.compareTo(TradeItemCalculator.safeBigDecimal(sourceSalesOrderItem.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细可开票金额不足");
        }

        requestProgressMap.merge(
                sourceSalesOrderItemId,
                new AllocationProgress(resolvedItem.weightTon(), resolvedItem.amount()),
                AllocationProgress::merge
        );
    }

    private ResolvedInvoiceIssueItem resolveItem(InvoiceIssueItemRequest source,
                                                 Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                                 int lineNo) {
        Long sourceSalesOrderItemId = source.sourceSalesOrderItemId();
        if (sourceSalesOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不能为空");
        }
        SalesOrderItem sourceSalesOrderItem = sourceSalesOrderItemMap.get(sourceSalesOrderItemId);
        if (sourceSalesOrderItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不存在");
        }
        BigDecimal pieceWeightTon = TradeItemCalculator.scaleWeightTon(sourceSalesOrderItem.getPieceWeightTon());
        BigDecimal unitPrice = TradeItemCalculator.scaleAmount(sourceSalesOrderItem.getUnitPrice());
        BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), pieceWeightTon);
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, unitPrice);
        return new ResolvedInvoiceIssueItem(
                sourceSalesOrderItem.getSalesOrder().getOrderNo(),
                sourceSalesOrderItem.getMaterialCode(),
                sourceSalesOrderItem.getBrand(),
                sourceSalesOrderItem.getCategory(),
                sourceSalesOrderItem.getMaterial(),
                sourceSalesOrderItem.getSpec(),
                sourceSalesOrderItem.getLength(),
                sourceSalesOrderItem.getUnit(),
                sourceSalesOrderItem.getWarehouseName(),
                sourceSalesOrderItem.getBatchNo(),
                TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()),
                sourceSalesOrderItem.getPiecesPerBundle(),
                pieceWeightTon,
                unitPrice,
                weightTon,
                amount
        );
    }

    private InvoiceIssueItemResponse toItemResponse(InvoiceIssueItem item) {
        return new InvoiceIssueItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceNo(),
                item.getSourceSalesOrderItemId(),
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

    private record ResolvedInvoiceIssueItem(
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
            Integer piecesPerBundle,
            BigDecimal pieceWeightTon,
            BigDecimal unitPrice,
            BigDecimal weightTon,
            BigDecimal amount
    ) {
    }
}
