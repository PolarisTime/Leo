package com.leo.erp.finance.invoiceissue.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssueItem;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.finance.invoiceissue.mapper.InvoiceIssueMapper;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueItemRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueItemResponse;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueResponse;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.system.company.service.CompanySettingService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
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

    public InvoiceIssueService(InvoiceIssueRepository repository,
                               SalesOrderItemQueryService salesOrderItemQueryService,
                               SnowflakeIdGenerator idGenerator,
                               InvoiceIssueMapper mapper,
                               CompanySettingService companySettingService) {
        super(idGenerator);
        this.repository = repository;
        this.salesOrderItemQueryService = salesOrderItemQueryService;
        this.mapper = mapper;
        this.companySettingService = companySettingService;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceIssueResponse> page(PageQuery query,
                                           String keyword,
                                           String customerName,
                                           String status,
                                           LocalDate startDate,
                                           LocalDate endDate) {
        Specification<InvoiceIssue> spec = Specs.<InvoiceIssue>notDeleted()
                .and(Specs.keywordLike(keyword, "issueNo", "invoiceNo", "sourceSalesOrderNos", "customerName", "projectName"))
                .and(Specs.equalIfPresent("customerName", customerName))
                .and(Specs.equalIfPresent("status", status))
                .and((root, criteriaQuery, criteriaBuilder) ->
                        startDate == null ? criteriaBuilder.conjunction() : criteriaBuilder.greaterThanOrEqualTo(root.get("invoiceDate"), startDate))
                .and((root, criteriaQuery, criteriaBuilder) ->
                        endDate == null ? criteriaBuilder.conjunction() : criteriaBuilder.lessThanOrEqualTo(root.get("invoiceDate"), endDate));
        return page(query, spec, repository);
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
    protected String notFoundMessage() {
        return "开票单不存在";
    }

    @Override
    protected void apply(InvoiceIssue entity, InvoiceIssueRequest request) {
        entity.setIssueNo(request.issueNo());
        entity.setInvoiceNo(request.invoiceNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setInvoiceDate(request.invoiceDate());
        entity.setInvoiceType(request.invoiceType());
        entity.setStatus(request.status());
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());

        List<Long> sourceItemIds = request.items().stream()
                .map(InvoiceIssueItemRequest::sourceSalesOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, SalesOrderItem> sourceSalesOrderItemMap = loadSourceSalesOrderItemMap(sourceItemIds);
        Map<Long, InvoiceAllocationProgress> allocatedProgressMap = loadAllocatedProgressMap(sourceItemIds, entity.getId());
        Map<Long, InvoiceAllocationProgress> requestProgressMap = new HashMap<>();
        LinkedHashSet<String> sourceSalesOrderNos = new LinkedHashSet<>();
        List<InvoiceIssueItem> items = new ArrayList<>();
        BigDecimal amount = BigDecimal.ZERO;
        for (int i = 0; i < request.items().size(); i++) {
            InvoiceIssueItemRequest source = request.items().get(i);
            ResolvedInvoiceIssueItem resolvedItem = resolveItem(source);
            validateSourceSalesOrderAllocation(
                    source,
                    i + 1,
                    resolvedItem,
                    sourceSalesOrderItemMap,
                    allocatedProgressMap,
                    requestProgressMap
            );
            InvoiceIssueItem item = new InvoiceIssueItem();
            item.setId(nextId());
            item.setInvoiceIssue(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(source.sourceNo());
            item.setSourceSalesOrderItemId(source.sourceSalesOrderItemId());
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
            items.add(item);
            sourceSalesOrderNos.add(source.sourceNo());
            amount = amount.add(lineAmount);
        }
        entity.getItems().clear();
        entity.getItems().addAll(items);
        entity.setSourceSalesOrderNos(String.join(", ", sourceSalesOrderNos));
        entity.setAmount(amount);
        validateDeclaredAmount("开票", request.amount(), amount);
        entity.setTaxAmount(calculateTaxAmount(amount, request.taxAmount()));
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

    private Map<Long, InvoiceAllocationProgress> loadAllocatedProgressMap(List<Long> sourceItemIds, Long currentIssueId) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }
        return repository.summarizeAllocatedBySourceSalesOrderItemIds(
                        sourceItemIds,
                        currentIssueId
                ).stream()
                .collect(HashMap::new, (map, summary) -> map.put(
                        summary.getSourceSalesOrderItemId(),
                        new InvoiceAllocationProgress(safe(summary.getTotalWeightTon()), safe(summary.getTotalAmount()))
                ), HashMap::putAll);
    }

    private void validateSourceSalesOrderAllocation(InvoiceIssueItemRequest source,
                                                    int lineNo,
                                                    ResolvedInvoiceIssueItem resolvedItem,
                                                    Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                                    Map<Long, InvoiceAllocationProgress> allocatedProgressMap,
                                                    Map<Long, InvoiceAllocationProgress> requestProgressMap) {
        Long sourceSalesOrderItemId = source.sourceSalesOrderItemId();
        if (sourceSalesOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不能为空");
        }

        SalesOrderItem sourceSalesOrderItem = sourceSalesOrderItemMap.get(sourceSalesOrderItemId);
        if (sourceSalesOrderItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不存在");
        }

        InvoiceAllocationProgress allocatedProgress = allocatedProgressMap.getOrDefault(
                sourceSalesOrderItemId,
                InvoiceAllocationProgress.EMPTY
        );
        InvoiceAllocationProgress requestProgress = requestProgressMap.getOrDefault(
                sourceSalesOrderItemId,
                InvoiceAllocationProgress.EMPTY
        );
        BigDecimal nextWeightTon = allocatedProgress.weightTon()
                .add(requestProgress.weightTon())
                .add(resolvedItem.weightTon());
        if (nextWeightTon.compareTo(safe(sourceSalesOrderItem.getWeightTon())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细可开票吨位不足");
        }

        BigDecimal nextAmount = allocatedProgress.amount()
                .add(requestProgress.amount())
                .add(resolvedItem.amount());
        if (nextAmount.compareTo(safe(sourceSalesOrderItem.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细可开票金额不足");
        }

        requestProgressMap.merge(
                sourceSalesOrderItemId,
                new InvoiceAllocationProgress(resolvedItem.weightTon(), resolvedItem.amount()),
                InvoiceAllocationProgress::merge
        );
    }

    private ResolvedInvoiceIssueItem resolveItem(InvoiceIssueItemRequest source) {
        BigDecimal weightTon = resolveWeightTon(source.quantity(), source.pieceWeightTon(), source.weightTon());
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.unitPrice());
        return new ResolvedInvoiceIssueItem(weightTon, amount);
    }

    private BigDecimal resolveWeightTon(Integer quantity, BigDecimal pieceWeightTon, BigDecimal weightTon) {
        if (weightTon != null && weightTon.compareTo(BigDecimal.ZERO) > 0) {
            return weightTon.setScale(3, RoundingMode.HALF_UP);
        }
        return TradeItemCalculator.calculateWeightTon(quantity, pieceWeightTon);
    }

    private BigDecimal calculateTaxAmount(BigDecimal amount, BigDecimal requestedTaxAmount) {
        BigDecimal taxRate = companySettingService.resolveCurrentTaxRate();
        if (taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return requestedTaxAmount == null ? BigDecimal.ZERO : requestedTaxAmount;
        }
        return amount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    }

    private void validateDeclaredAmount(String fieldLabel, BigDecimal requestValue, BigDecimal calculatedValue) {
        if (requestValue != null && requestValue.compareTo(calculatedValue) != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldLabel + "与明细计算结果不一致");
        }
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
            BigDecimal weightTon,
            BigDecimal amount
    ) {
    }

    private record InvoiceAllocationProgress(
            BigDecimal weightTon,
            BigDecimal amount
    ) {
        private static final InvoiceAllocationProgress EMPTY = new InvoiceAllocationProgress(BigDecimal.ZERO, BigDecimal.ZERO);

        private InvoiceAllocationProgress merge(InvoiceAllocationProgress other) {
            return new InvoiceAllocationProgress(weightTon.add(other.weightTon), amount.add(other.amount));
        }
    }
}
