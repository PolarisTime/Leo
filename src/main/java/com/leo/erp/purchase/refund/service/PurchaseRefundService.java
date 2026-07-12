package com.leo.erp.purchase.refund.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.service.PaymentPurchasePrepaymentService;
import com.leo.erp.finance.supplierrefundreceipt.service.SupplierRefundReceiptGuard;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.service.PurchaseInboundCompletionSyncService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import com.leo.erp.purchase.refund.domain.entity.PurchaseRefund;
import com.leo.erp.purchase.refund.domain.entity.PurchaseRefundItem;
import com.leo.erp.purchase.refund.mapper.PurchaseRefundMapper;
import com.leo.erp.purchase.refund.repository.PurchaseRefundRepository;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundRequest;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundItemResponse;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundPreviewResponse;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundResponse;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundSourceCandidateResponse;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Service
public class PurchaseRefundService extends AbstractCrudService<
        PurchaseRefund, PurchaseRefundRequest, PurchaseRefundResponse> {

    private static final String MODULE_KEY = "purchase-refund";
    private static final String[] SEARCH_FIELDS = {"refundNo", "purchaseOrderNo", "supplierCode", "supplierName"};
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            StatusConstants.DRAFT,
            StatusConstants.AUDITED
    );
    private static final Set<String> ALLOWED_SOURCE_STATUSES = Set.of(
            StatusConstants.AUDITED,
            StatusConstants.PURCHASE_COMPLETED
    );
    private static final int SOURCE_CANDIDATE_BATCH_SIZE = 200;

    private final PurchaseRefundRepository repository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final PurchaseInboundItemRepository inboundItemRepository;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final PurchaseRefundCalculator calculator;
    private final PurchaseRefundInvoiceCapacityGuard invoiceCapacityGuard;
    private final PurchaseRefundMapper mapper;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final SupplierRepository supplierRepository;
    private final SupplierRefundReceiptGuard supplierRefundReceiptGuard;
    private final PaymentPurchasePrepaymentService purchasePrepaymentService;
    private final PurchaseInboundCompletionSyncService inboundCompletionSyncService;

    public PurchaseRefundService(PurchaseRefundRepository repository,
                                 PurchaseOrderRepository purchaseOrderRepository,
                                 PurchaseOrderItemRepository purchaseOrderItemRepository,
                                 PurchaseInboundItemRepository inboundItemRepository,
                                 SnowflakeIdGenerator idGenerator,
                                 SourceAllocationLockService sourceAllocationLockService,
                                 PurchaseRefundCalculator calculator,
                                 PurchaseRefundInvoiceCapacityGuard invoiceCapacityGuard,
                                 PurchaseRefundMapper mapper,
                                 ResourceRecordAccessGuard resourceRecordAccessGuard,
                                 WorkflowTransitionGuard workflowTransitionGuard,
                                 SupplierRepository supplierRepository) {
        this(
                repository,
                purchaseOrderRepository,
                purchaseOrderItemRepository,
                inboundItemRepository,
                idGenerator,
                sourceAllocationLockService,
                calculator,
                invoiceCapacityGuard,
                mapper,
                resourceRecordAccessGuard,
                workflowTransitionGuard,
                supplierRepository,
                null,
                null,
                null
        );
    }

    public PurchaseRefundService(PurchaseRefundRepository repository,
                                 PurchaseOrderRepository purchaseOrderRepository,
                                 PurchaseOrderItemRepository purchaseOrderItemRepository,
                                 PurchaseInboundItemRepository inboundItemRepository,
                                 SnowflakeIdGenerator idGenerator,
                                 SourceAllocationLockService sourceAllocationLockService,
                                 PurchaseRefundCalculator calculator,
                                 PurchaseRefundInvoiceCapacityGuard invoiceCapacityGuard,
                                 PurchaseRefundMapper mapper,
                                 ResourceRecordAccessGuard resourceRecordAccessGuard,
                                 WorkflowTransitionGuard workflowTransitionGuard,
                                 SupplierRepository supplierRepository,
                                 SupplierRefundReceiptGuard supplierRefundReceiptGuard) {
        this(
                repository,
                purchaseOrderRepository,
                purchaseOrderItemRepository,
                inboundItemRepository,
                idGenerator,
                sourceAllocationLockService,
                calculator,
                invoiceCapacityGuard,
                mapper,
                resourceRecordAccessGuard,
                workflowTransitionGuard,
                supplierRepository,
                supplierRefundReceiptGuard,
                null,
                null
        );
    }

    @Autowired
    public PurchaseRefundService(PurchaseRefundRepository repository,
                                 PurchaseOrderRepository purchaseOrderRepository,
                                 PurchaseOrderItemRepository purchaseOrderItemRepository,
                                 PurchaseInboundItemRepository inboundItemRepository,
                                 SnowflakeIdGenerator idGenerator,
                                 SourceAllocationLockService sourceAllocationLockService,
                                 PurchaseRefundCalculator calculator,
                                 PurchaseRefundInvoiceCapacityGuard invoiceCapacityGuard,
                                 PurchaseRefundMapper mapper,
                                 ResourceRecordAccessGuard resourceRecordAccessGuard,
                                 WorkflowTransitionGuard workflowTransitionGuard,
                                 SupplierRepository supplierRepository,
                                 SupplierRefundReceiptGuard supplierRefundReceiptGuard,
                                 PaymentPurchasePrepaymentService purchasePrepaymentService,
                                 PurchaseInboundCompletionSyncService inboundCompletionSyncService) {
        super(idGenerator);
        this.repository = repository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.inboundItemRepository = inboundItemRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.calculator = calculator;
        this.invoiceCapacityGuard = invoiceCapacityGuard;
        this.mapper = mapper;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.supplierRepository = supplierRepository;
        this.supplierRefundReceiptGuard = supplierRefundReceiptGuard;
        this.purchasePrepaymentService = purchasePrepaymentService;
        this.inboundCompletionSyncService = inboundCompletionSyncService;
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRefundResponse> page(PageQuery query, PageFilter filter) {
        Specification<PurchaseRefund> spec = Specs.<PurchaseRefund>keywordLike(
                        filter.keyword(),
                        SEARCH_FIELDS
                )
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("refundDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    @Transactional(readOnly = true)
    public List<PurchaseRefundResponse> search(String keyword, int maxSize) {
        return search(keyword, SEARCH_FIELDS, maxSize, null, repository);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRefundSourceCandidateResponse> sourceCandidates(PageQuery query, PageFilter filter) {
        Specification<PurchaseOrder> sourceSpecification = Specs.<PurchaseOrder>notDeleted()
                .and((root, criteriaQuery, criteriaBuilder) -> root.get("status").in(ALLOWED_SOURCE_STATUSES))
                .and(Specs.keywordLike(filter.keyword(), "orderNo", "supplierName"))
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("supplierId", filter.supplierId()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.dateTimeBetweenDatesIfPresent(
                        "orderDate",
                        filter.startDate(),
                        filter.endDate()
                ));
        long requestedStart = (long) query.page() * query.size();
        long requestedEnd = requestedStart + query.size();
        long candidateCount = 0L;
        List<PurchaseRefundSourceCandidateResponse> requestedCandidates = new ArrayList<>(query.size());
        int pageIndex = 0;
        Page<PurchaseOrder> batch;
        do {
            batch = purchaseOrderRepository.findAll(
                    DataScopeContext.apply(sourceSpecification),
                    sourceBatchPageable(query, pageIndex)
            );
            List<PurchaseOrder> sourceOrders = batch.getContent().stream()
                    .filter(order -> ALLOWED_SOURCE_STATUSES.contains(order.getStatus()))
                    .toList();
            sourceOrders.forEach(order -> resourceRecordAccessGuard.assertCurrentUserCanAccess(
                    "purchase-order",
                    "read",
                    order
            ));
            for (PurchaseRefundSourceCandidateResponse candidate : calculateSourceCandidates(sourceOrders)) {
                if (candidateCount >= requestedStart && candidateCount < requestedEnd) {
                    requestedCandidates.add(candidate);
                }
                candidateCount++;
            }
            pageIndex++;
        } while (batch.hasNext());
        return new PageImpl<>(requestedCandidates, query.toPageable("id"), candidateCount);
    }

    private Pageable sourceBatchPageable(PageQuery query, int pageIndex) {
        Sort sourceSort = query.toPageable("id").getSort();
        if (sourceSort.getOrderFor("id") == null) {
            Sort.Direction tieBreakerDirection = sourceSort.stream()
                    .findFirst()
                    .map(Sort.Order::getDirection)
                    .orElse(Sort.Direction.DESC);
            sourceSort = sourceSort.and(Sort.by(tieBreakerDirection, "id"));
        }
        return PageRequest.of(pageIndex, SOURCE_CANDIDATE_BATCH_SIZE, sourceSort);
    }

    private List<PurchaseRefundSourceCandidateResponse> calculateSourceCandidates(
            List<PurchaseOrder> sourceOrders
    ) {
        if (sourceOrders.isEmpty()) {
            return List.of();
        }
        List<Long> sourceOrderIds = sourceOrders.stream().map(PurchaseOrder::getId).toList();
        Set<Long> unavailableOrderIds = Set.copyOf(
                repository.findActiveSourcePurchaseOrderIdsBySourcePurchaseOrderIdIn(sourceOrderIds)
        );
        List<Long> sourceItemIds = sourceOrders.stream()
                .filter(order -> !unavailableOrderIds.contains(order.getId()))
                .flatMap(order -> order.getItems().stream())
                .map(PurchaseOrderItem::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, List<PurchaseInboundItem>> inboundItemsBySourceItemId = new HashMap<>();
        if (!sourceItemIds.isEmpty()) {
            inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(sourceItemIds)
                    .forEach(item -> inboundItemsBySourceItemId
                            .computeIfAbsent(item.getSourcePurchaseOrderItemId(), ignored -> new ArrayList<>())
                            .add(item));
        }
        return sourceOrders.stream()
                .filter(order -> !unavailableOrderIds.contains(order.getId()))
                .map(order -> toSourceCandidate(order, inboundItemsBySourceItemId))
                .filter(candidate -> candidate != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseRefundPreviewResponse preview(Long sourcePurchaseOrderId) {
        PurchaseOrder sourceOrder = purchaseOrderRepository
                .findByIdAndDeletedFlagFalse(sourcePurchaseOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "来源采购订单不存在"));
        resourceRecordAccessGuard.assertCurrentUserCanAccess("purchase-order", "read", sourceOrder);
        assertSourceOrderStatus(sourceOrder);
        PurchaseRefundCalculator.Calculation calculation = calculate(sourceOrder);
        SupplierIdentity supplier = requireSourceSupplierIdentity(sourceOrder);
        List<PurchaseRefundItemResponse> items = new ArrayList<>();
        for (int index = 0; index < calculation.lines().size(); index++) {
            PurchaseRefundCalculator.Line line = calculation.lines().get(index);
            PurchaseOrderItem sourceItem = line.sourceItem();
            items.add(new PurchaseRefundItemResponse(
                    null,
                    index + 1,
                    sourceItem.getId(),
                    sourceItem.getMaterialId(),
                    sourceItem.getMaterialCode(),
                    sourceItem.getBrand(),
                    sourceItem.getCategory(),
                    sourceItem.getMaterial(),
                    sourceItem.getSpec(),
                    sourceItem.getLength(),
                    sourceItem.getUnit(),
                    sourceItem.getWarehouseId(),
                    sourceItem.getWarehouseName(),
                    sourceItem.getBatchNo(),
                    sourceItem.getBatchNoNormalized(),
                    line.quantity(),
                    sourceItem.getQuantityUnit(),
                    sourceItem.getPieceWeightTon(),
                    sourceItem.getPiecesPerBundle(),
                    line.weightTon(),
                    sourceItem.getUnitPrice(),
                    line.amount()
            ));
        }
        return new PurchaseRefundPreviewResponse(
                sourceOrder.getId(),
                sourceOrder.getOrderNo(),
                supplier.id(),
                supplier.code(),
                supplier.name(),
                sourceOrder.getSettlementCompanyId(),
                sourceOrder.getSettlementCompanyName(),
                calculation.totalQuantity(),
                calculation.totalWeight(),
                calculation.totalAmount(),
                List.copyOf(items)
        );
    }

    @Override
    protected void validateCreate(PurchaseRefundRequest request) {
        ensureRefundNoUnique(request.refundNo());
    }

    @Override
    protected void validateUpdate(PurchaseRefund entity, PurchaseRefundRequest request) {
        if (!entity.getRefundNo().equals(request.refundNo())) {
            ensureRefundNoUnique(request.refundNo());
        }
    }

    @Override
    protected PurchaseRefundRequest normalizeCreateRequest(PurchaseRefundRequest request, long entityId) {
        return new PurchaseRefundRequest(
                resolveCreateBusinessNo(MODULE_KEY, request.refundNo(), entityId),
                request.sourcePurchaseOrderId(),
                request.refundDate(),
                request.status(),
                request.operatorName(),
                request.remark()
        );
    }

    @Override
    protected PurchaseRefundRequest normalizeUpdateRequest(PurchaseRefund entity, PurchaseRefundRequest request) {
        return new PurchaseRefundRequest(
                entity.getRefundNo(),
                request.sourcePurchaseOrderId(),
                request.refundDate(),
                request.status(),
                request.operatorName(),
                request.remark()
        );
    }

    @Override
    protected PurchaseRefund newEntity() {
        return new PurchaseRefund();
    }

    @Override
    protected void assignId(PurchaseRefund entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<PurchaseRefund> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<PurchaseRefund> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "采购退款单不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected Set<String> allowedStatusTransitions() {
        return StatusConstants.DRAFT_AUDIT_TRANSITIONS;
    }

    @Override
    protected void apply(PurchaseRefund entity, PurchaseRefundRequest request) {
        PurchaseOrder sourceOrder = lockAndRequireSourceOrder(entity, request.sourcePurchaseOrderId());
        assertSourceOrderStatus(sourceOrder);
        assertOnlyActiveRefund(entity, sourceOrder.getId());
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                entity.getStatus() == null ? StatusConstants.DRAFT : entity.getStatus(),
                "采购退款状态",
                ALLOWED_STATUSES
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                MODULE_KEY,
                entity.getStatus(),
                nextStatus,
                StatusConstants.AUDITED
        );
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            assertSourcePurchaseOrderFullyPaid(sourceOrder);
        }
        PurchaseRefundCalculator.Calculation calculation = calculate(sourceOrder);
        requirePositiveRefund(calculation);
        invoiceCapacityGuard.assertRefundFits(sourceOrder, calculation);
        entity.setRefundNo(request.refundNo());
        entity.setRefundDate(request.refundDate());
        entity.setStatus(nextStatus);
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());
        applyCalculation(entity, sourceOrder, calculation);
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            sourceOrder.setStatus(StatusConstants.PURCHASE_COMPLETED);
        }
    }

    @Override
    protected void beforeStatusUpdate(PurchaseRefund entity, String currentStatus, String nextStatus) {
        PurchaseOrder sourceOrder = lockAndRequireSourceOrder(entity, entity.getSourcePurchaseOrderId());
        if (StatusConstants.AUDITED.equals(currentStatus) && StatusConstants.DRAFT.equals(nextStatus)) {
            assertNoSupplierRefundReceipt(entity, "反审核");
        }
        assertSourceOrderStatus(sourceOrder);
        assertOnlyActiveRefund(entity, sourceOrder.getId());
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            assertSourcePurchaseOrderFullyPaid(sourceOrder);
        }
        PurchaseRefundCalculator.Calculation calculation = calculate(sourceOrder);
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            requirePositiveRefund(calculation);
            invoiceCapacityGuard.assertRefundFits(sourceOrder, calculation);
        }
        applyCalculation(entity, sourceOrder, calculation);
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            sourceOrder.setStatus(StatusConstants.PURCHASE_COMPLETED);
        } else if (StatusConstants.DRAFT.equals(nextStatus)) {
            sourceOrder.setStatus(calculation.fullyInbound()
                    ? StatusConstants.PURCHASE_COMPLETED
                    : StatusConstants.AUDITED);
        }
    }

    @Override
    protected void beforeDelete(PurchaseRefund entity) {
        lockAndRequireSourceOrder(entity, entity.getSourcePurchaseOrderId());
        assertNoSupplierRefundReceipt(entity, "删除");
    }

    @Override
    protected PurchaseRefund saveEntity(PurchaseRefund entity) {
        PurchaseRefund saved = repository.save(entity);
        repository.flush();
        if (inboundCompletionSyncService != null) {
            inboundCompletionSyncService.synchronizeAfterPurchaseRefundStatusChange(
                    entity.getItems().stream()
                            .map(PurchaseRefundItem::getSourcePurchaseOrderItemId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList()
            );
        }
        return saved;
    }

    @Override
    protected PurchaseRefundResponse toResponse(PurchaseRefund entity) {
        return mapper.toSummaryResponse(entity);
    }

    @Override
    protected PurchaseRefundResponse toDetailResponse(PurchaseRefund entity) {
        return mapper.toDetailResponse(entity);
    }

    @Override
    protected PurchaseRefundResponse toSavedResponse(PurchaseRefund entity) {
        return toDetailResponse(entity);
    }

    private PurchaseOrder lockAndRequireSourceOrder(PurchaseRefund entity, Long targetOrderId) {
        if (targetOrderId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "来源采购订单不能为空");
        }
        TreeSet<Long> affectedOrderIds = new TreeSet<>();
        if (entity != null && entity.getSourcePurchaseOrderId() != null) {
            affectedOrderIds.add(entity.getSourcePurchaseOrderId());
        }
        affectedOrderIds.add(targetOrderId);
        TreeSet<Long> sourceItemIds = new TreeSet<>();
        for (Long orderId : affectedOrderIds) {
            sourceItemIds.addAll(purchaseOrderItemRepository.findActiveIdsByPurchaseOrderId(orderId));
        }
        sourceAllocationLockService.lockTradeItemSources(
                List.copyOf(sourceItemIds),
                List.of(),
                List.of()
        );
        Map<Long, PurchaseOrder> orders = new TreeMap<>();
        for (Long orderId : affectedOrderIds) {
            PurchaseOrder order = purchaseOrderRepository.findByIdAndDeletedFlagFalse(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "来源采购订单不存在"));
            resourceRecordAccessGuard.assertCurrentUserCanAccess("purchase-order", "read", order);
            orders.put(orderId, order);
        }
        return orders.get(targetOrderId);
    }

    private void assertSourceOrderStatus(PurchaseOrder sourceOrder) {
        if (!ALLOWED_SOURCE_STATUSES.contains(sourceOrder.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购订单未审核，不能退款");
        }
    }

    private void assertOnlyActiveRefund(PurchaseRefund entity, Long sourceOrderId) {
        boolean exists = entity.getSourcePurchaseOrderId() == null
                ? repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalse(sourceOrderId)
                : repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalseAndIdNot(
                        sourceOrderId,
                        entity.getId()
                );
        if (exists) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该采购订单已存在未删除的采购退款单");
        }
    }

    private PurchaseRefundCalculator.Calculation calculate(PurchaseOrder sourceOrder) {
        List<Long> sourceItemIds = sourceOrder.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<PurchaseInboundItem> inboundItems = sourceItemIds.isEmpty()
                ? List.of()
                : inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(sourceItemIds);
        return calculator.calculate(sourceOrder, inboundItems);
    }

    private void requirePositiveRefund(PurchaseRefundCalculator.Calculation calculation) {
        if (!calculation.hasPositiveRefund()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该采购订单没有可退款的数量或重量");
        }
    }

    private void applyCalculation(PurchaseRefund entity,
                                  PurchaseOrder sourceOrder,
                                  PurchaseRefundCalculator.Calculation calculation) {
        SupplierIdentity supplier = resolveSupplierIdentity(entity, sourceOrder);
        entity.setSourcePurchaseOrderId(sourceOrder.getId());
        entity.setPurchaseOrderNo(sourceOrder.getOrderNo());
        entity.setSupplierId(supplier.id());
        entity.setSupplierCode(supplier.code());
        entity.setSupplierName(supplier.name());
        entity.setSettlementCompanyId(sourceOrder.getSettlementCompanyId());
        entity.setSettlementCompanyName(sourceOrder.getSettlementCompanyName());
        entity.setTotalQuantity(calculation.totalQuantity());
        entity.setTotalWeight(calculation.totalWeight());
        entity.setTotalAmount(calculation.totalAmount());

        Map<Long, PurchaseRefundItem> existingItems = new LinkedHashMap<>();
        if (entity.getItems() != null) {
            entity.getItems().stream()
                    .filter(item -> item.getSourcePurchaseOrderItemId() != null)
                    .forEach(item -> existingItems.putIfAbsent(item.getSourcePurchaseOrderItemId(), item));
        } else {
            entity.setItems(new ArrayList<>());
        }
        List<PurchaseRefundItem> nextItems = new ArrayList<>();
        for (int index = 0; index < calculation.lines().size(); index++) {
            PurchaseRefundCalculator.Line line = calculation.lines().get(index);
            PurchaseOrderItem sourceItem = line.sourceItem();
            PurchaseRefundItem item = existingItems.remove(sourceItem.getId());
            if (item == null) {
                item = new PurchaseRefundItem();
                item.setId(nextId());
            }
            item.setPurchaseRefund(entity);
            item.setSourcePurchaseOrderItemId(sourceItem.getId());
            item.setLineNo(index + 1);
            item.setMaterialId(sourceItem.getMaterialId());
            item.setMaterialCode(sourceItem.getMaterialCode());
            item.setBrand(sourceItem.getBrand());
            item.setCategory(sourceItem.getCategory());
            item.setMaterial(sourceItem.getMaterial());
            item.setSpec(sourceItem.getSpec());
            item.setLength(sourceItem.getLength());
            item.setUnit(sourceItem.getUnit());
            item.setWarehouseId(sourceItem.getWarehouseId());
            item.setWarehouseName(sourceItem.getWarehouseName());
            item.setBatchNo(sourceItem.getBatchNo());
            item.setQuantity(line.quantity());
            item.setQuantityUnit(sourceItem.getQuantityUnit());
            item.setPieceWeightTon(sourceItem.getPieceWeightTon());
            item.setPiecesPerBundle(sourceItem.getPiecesPerBundle());
            item.setWeightTon(line.weightTon());
            item.setUnitPrice(sourceItem.getUnitPrice());
            item.setAmount(line.amount());
            nextItems.add(item);
        }
        entity.getItems().clear();
        entity.getItems().addAll(nextItems);
    }

    private void ensureRefundNoUnique(String refundNo) {
        if (repository.existsByRefundNoAndDeletedFlagFalse(refundNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购退款单号已存在");
        }
    }

    private void assertNoSupplierRefundReceipt(PurchaseRefund entity, String operationName) {
        if (supplierRefundReceiptGuard != null) {
            supplierRefundReceiptGuard.assertNoActiveReceipt(entity.getId(), operationName);
        }
    }

    private void assertSourcePurchaseOrderFullyPaid(PurchaseOrder sourceOrder) {
        if (purchasePrepaymentService != null) {
            purchasePrepaymentService.assertSourcePurchaseOrderFullyPaid(sourceOrder);
        }
    }

    private SupplierIdentity resolveSupplierIdentity(PurchaseRefund entity, PurchaseOrder sourceOrder) {
        if (sourceOrder.getSupplierId() != null) {
            SupplierIdentity sourceIdentity = requireSourceSupplierIdentity(sourceOrder);
            if (Objects.equals(entity.getSourcePurchaseOrderId(), sourceOrder.getId())
                    && entity.getSupplierId() != null
                    && !Objects.equals(entity.getSupplierId(), sourceIdentity.id())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购退款单供应商ID与来源采购订单不一致");
            }
            return sourceIdentity;
        }
        if (!Objects.equals(entity.getSourcePurchaseOrderId(), sourceOrder.getId())) {
            return requireSourceSupplierIdentity(sourceOrder);
        }
        String supplierCode = normalizeRequiredSupplierText(
                entity.getSupplierCode(),
                "采购退款单供应商编码缺失"
        );
        String boundSupplierName = normalizeRequiredSupplierText(
                entity.getSupplierName(),
                "采购退款单供应商名称缺失"
        );
        Supplier supplier = supplierRepository.findBySupplierCodeAndDeletedFlagFalse(supplierCode)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "采购退款单供应商编码已失效，请先修复供应商主数据"
                ));
        normalizeRequiredSupplierText(supplier.getSupplierCode(), "供应商编码不能为空");
        String sourceSupplierCode = normalizeRequiredSupplierText(
                sourceOrder.getSupplierCode() == null ? supplierCode : sourceOrder.getSupplierCode(),
                "来源采购订单供应商编码缺失"
        );
        String sourceSupplierName = normalizeRequiredSupplierText(
                sourceOrder.getSupplierName(),
                "来源采购订单供应商名称缺失"
        );
        if (!supplierCode.equals(sourceSupplierCode) || !sourceSupplierName.equals(boundSupplierName)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购退款单供应商编码与来源采购订单不一致"
            );
        }
        return new SupplierIdentity(supplier.getId(), supplierCode, boundSupplierName);
    }

    private SupplierIdentity requireSourceSupplierIdentity(PurchaseOrder sourceOrder) {
        if (sourceOrder.getSupplierId() != null) {
            Supplier supplier = supplierRepository.findByIdAndDeletedFlagFalse(sourceOrder.getSupplierId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.BUSINESS_ERROR,
                            "来源采购订单供应商ID已失效"
                    ));
            String sourceCode = normalizeRequiredSupplierText(
                    sourceOrder.getSupplierCode() == null
                            ? supplier.getSupplierCode()
                            : sourceOrder.getSupplierCode(),
                    "来源采购订单供应商编码缺失"
            );
            if (!Objects.equals(sourceCode, normalizeRequiredSupplierText(
                    supplier.getSupplierCode(),
                    "供应商编码不能为空"
            ))) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购订单供应商ID与编码不一致");
            }
            return new SupplierIdentity(
                    supplier.getId(),
                    sourceCode,
                    normalizeRequiredSupplierText(sourceOrder.getSupplierName(), "来源采购订单供应商名称缺失")
            );
        }
        String sourceSupplierCode = sourceOrder.getSupplierCode();
        if (sourceSupplierCode == null || sourceSupplierCode.isBlank()) {
            Supplier supplier = supplierRepository
                    .findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(sourceOrder.getSupplierName())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.BUSINESS_ERROR,
                            "来源采购订单供应商主数据不存在"
                    ));
            return toSupplierIdentity(supplier);
        }
        String normalizedCode = normalizeRequiredSupplierText(sourceSupplierCode, "来源采购订单供应商编码缺失");
        supplierRepository.findBySupplierCodeAndDeletedFlagFalse(normalizedCode)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "来源采购订单供应商编码已失效"
                ));
        return new SupplierIdentity(
                supplierRepository.findBySupplierCodeAndDeletedFlagFalse(normalizedCode)
                        .map(Supplier::getId)
                        .orElse(null),
                normalizedCode,
                normalizeRequiredSupplierText(sourceOrder.getSupplierName(), "来源采购订单供应商名称缺失")
        );
    }

    private SupplierIdentity toSupplierIdentity(Supplier supplier) {
        return new SupplierIdentity(
                supplier.getId(),
                normalizeRequiredSupplierText(supplier.getSupplierCode(), "供应商编码不能为空"),
                normalizeRequiredSupplierText(supplier.getSupplierName(), "供应商名称不能为空")
        );
    }

    private String normalizeRequiredSupplierText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
        return value.trim();
    }

    private PurchaseRefundSourceCandidateResponse toSourceCandidate(
            PurchaseOrder sourceOrder,
            Map<Long, List<PurchaseInboundItem>> inboundItemsBySourceItemId
    ) {
        List<PurchaseInboundItem> inboundItems = sourceOrder.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .filter(id -> id != null)
                .flatMap(id -> inboundItemsBySourceItemId.getOrDefault(id, List.of()).stream())
                .toList();
        PurchaseRefundCalculator.Calculation calculation = calculator.calculate(sourceOrder, inboundItems);
        if (!calculation.hasPositiveRefund()) {
            return null;
        }
        return new PurchaseRefundSourceCandidateResponse(
                sourceOrder.getId(),
                sourceOrder.getOrderNo(),
                sourceOrder.getSupplierId(),
                sourceOrder.getSupplierCode(),
                sourceOrder.getSupplierName(),
                sourceOrder.getSettlementCompanyId(),
                sourceOrder.getSettlementCompanyName(),
                sourceOrder.getOrderDate(),
                sourceOrder.getStatus(),
                calculation.totalQuantity(),
                calculation.totalWeight(),
                calculation.totalAmount()
        );
    }

    private record SupplierIdentity(Long id, String code, String name) {
    }
}
