package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundImportBatch;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundImportBatchRepository;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundImportBatchRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundImportBatchResponse;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundSplitPreviewResponse;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PurchaseInboundImportBatchService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseInboundRepository purchaseInboundRepository;
    private final PurchaseInboundImportBatchRepository batchRepository;
    private final PurchaseInboundAllocationService allocationService;
    private final PurchaseInboundWeightSettlementService weightSettlementService;
    private final PurchaseInboundService purchaseInboundService;
    private final SnowflakeIdGenerator idGenerator;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;

    public PurchaseInboundImportBatchService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseInboundRepository purchaseInboundRepository,
            PurchaseInboundImportBatchRepository batchRepository,
            PurchaseInboundAllocationService allocationService,
            PurchaseInboundWeightSettlementService weightSettlementService,
            PurchaseInboundService purchaseInboundService,
            SnowflakeIdGenerator idGenerator,
            SourceAllocationLockService sourceAllocationLockService,
            ResourceRecordAccessGuard resourceRecordAccessGuard
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseInboundRepository = purchaseInboundRepository;
        this.batchRepository = batchRepository;
        this.allocationService = allocationService;
        this.weightSettlementService = weightSettlementService;
        this.purchaseInboundService = purchaseInboundService;
        this.idGenerator = idGenerator;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
    }

    @Transactional(readOnly = true)
    public PurchaseInboundSplitPreviewResponse preview(Long sourcePurchaseOrderId) {
        PurchaseOrder order = purchaseOrderRepository.findByIdAndDeletedFlagFalse(sourcePurchaseOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "来源采购订单不存在"));
        resourceRecordAccessGuard.assertCurrentUserCanAccess("purchase-order", "read", order);
        return buildPreview(order);
    }

    @Transactional
    public PurchaseInboundImportBatchResponse create(
            Long sourcePurchaseOrderId,
            PurchaseInboundImportBatchRequest request
    ) {
        if (request == null || request.inboundDate() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "入库日期不能为空");
        }
        PurchaseOrder order = purchaseOrderRepository.findByIdAndDeletedFlagFalseForUpdate(sourcePurchaseOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "来源采购订单不存在"));
        resourceRecordAccessGuard.assertCurrentUserCanAccess("purchase-order", "read", order);
        List<Long> sourceItemIds = requireStableSourceItemIds(order);
        sourceAllocationLockService.lockTradeItemSources(sourceItemIds, List.of(), List.of());

        PurchaseInboundSplitPreviewResponse preview = buildPreview(order);
        if (!preview.importAllowed()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, preview.blockingReason());
        }

        PurchaseInboundImportBatch batch = new PurchaseInboundImportBatch();
        batch.setId(idGenerator.nextId());
        batch.setBatchNo("PIB-" + batch.getId());
        batch.setSourcePurchaseOrderId(order.getId());
        batch.setSourcePurchaseOrderNo(order.getOrderNo());
        batchRepository.save(batch);

        List<PurchaseInboundImportBatchResponse.InboundDraft> created = new ArrayList<>();
        for (PurchaseInboundSplitPreviewResponse.Group group : preview.groups()) {
            PurchaseInboundResponse response = purchaseInboundService.createFromImportBatch(
                    toCreateRequest(order, group, request)
            );
            PurchaseInbound inbound = purchaseInboundRepository.findByIdAndDeletedFlagFalse(response.id())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.BUSINESS_ERROR,
                            "采购入库拆分草稿创建后无法读取，批次已回滚"
                    ));
            inbound.setImportBatchId(batch.getId());
            purchaseInboundRepository.save(inbound);
            created.add(toDraftResponse(inbound));
        }

        return new PurchaseInboundImportBatchResponse(
                batch.getId(),
                batch.getBatchNo(),
                batch.getSourcePurchaseOrderId(),
                batch.getSourcePurchaseOrderNo(),
                List.copyOf(created)
        );
    }

    @Transactional(readOnly = true)
    public PurchaseInboundImportBatchResponse detail(Long batchId) {
        PurchaseInboundImportBatch batch = batchRepository.findByIdAndDeletedFlagFalse(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "采购入库导入批次不存在"));
        PurchaseOrder order = purchaseOrderRepository.findByIdAndDeletedFlagFalse(batch.getSourcePurchaseOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "来源采购订单不存在"));
        resourceRecordAccessGuard.assertCurrentUserCanAccess("purchase-order", "read", order);
        List<PurchaseInboundImportBatchResponse.InboundDraft> inbounds = purchaseInboundRepository
                .findAllByImportBatchIdAndDeletedFlagFalseOrderById(batchId)
                .stream()
                .map(this::toDraftResponse)
                .toList();
        return new PurchaseInboundImportBatchResponse(
                batch.getId(),
                batch.getBatchNo(),
                batch.getSourcePurchaseOrderId(),
                batch.getSourcePurchaseOrderNo(),
                inbounds
        );
    }

    private PurchaseInboundSplitPreviewResponse buildPreview(PurchaseOrder order) {
        String blockingReason = validateOrderHeader(order);
        if (blockingReason != null) {
            return blockedPreview(order, blockingReason);
        }

        List<Long> sourceItemIds;
        try {
            sourceItemIds = requireStableSourceItemIds(order);
        } catch (BusinessException exception) {
            return blockedPreview(order, exception.getMessage());
        }
        Map<Long, Integer> allocatedQuantityMap = allocationService
                .loadAllocatedQuantityMap(sourceItemIds, null);
        if (allocatedQuantityMap.values().stream().anyMatch(quantity -> quantity != null && quantity > 0)) {
            return blockedPreview(order, "采购订单已存在采购入库单，不允许分批或重复入库");
        }
        Map<String, PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule> categoryRules =
                weightSettlementService.loadPurchaseWeighCategoryRules(
                        order.getItems().stream().map(PurchaseOrderItem::getCategory).toList()
                );

        LinkedHashMap<GroupKey, MutableGroup> groups = new LinkedHashMap<>();
        List<PurchaseOrderItem> sourceItems = order.getItems().stream()
                .sorted(Comparator.comparing(
                        PurchaseOrderItem::getLineNo,
                        Comparator.nullsLast(Integer::compareTo)
                ).thenComparing(PurchaseOrderItem::getId))
                .toList();
        for (PurchaseOrderItem source : sourceItems) {
            String itemError = validateSourceItem(source);
            if (itemError != null) {
                return blockedPreview(order, itemError);
            }
            int sourceQuantity = source.getQuantity();
            int allocatedQuantity = allocatedQuantityMap.getOrDefault(source.getId(), 0);
            if (allocatedQuantity > sourceQuantity) {
                return blockedPreview(
                        order,
                        "采购订单第" + source.getLineNo() + "行已分配数量超过订单数量，请先修复来源占用"
                );
            }
            int remainingQuantity = sourceQuantity - allocatedQuantity;
            if (remainingQuantity == 0) {
                continue;
            }
            boolean weighRequired = weightSettlementService.requiresPurchaseWeigh(
                    categoryRules,
                    source.getCategory()
            );
            GroupKey key = new GroupKey(source.getWarehouseId(), weighRequired);
            MutableGroup group = groups.computeIfAbsent(
                    key,
                    ignored -> new MutableGroup(
                            source.getWarehouseId(),
                            source.getWarehouseName(),
                            weighRequired ? "过磅" : "理算"
                    )
            );
            group.add(toPreviewItem(source, remainingQuantity));
        }
        if (groups.isEmpty()) {
            return blockedPreview(order, "采购订单没有可入库的剩余明细");
        }
        List<PurchaseInboundSplitPreviewResponse.Group> previewGroups = groups.values().stream()
                .map(MutableGroup::toResponse)
                .toList();
        return new PurchaseInboundSplitPreviewResponse(
                order.getId(),
                order.getOrderNo(),
                order.getSupplierId(),
                order.getSupplierCode(),
                order.getSupplierName(),
                order.getSettlementCompanyId(),
                order.getSettlementCompanyName(),
                true,
                null,
                previewGroups.size(),
                previewGroups
        );
    }

    private PurchaseInboundSplitPreviewResponse blockedPreview(PurchaseOrder order, String reason) {
        return new PurchaseInboundSplitPreviewResponse(
                order.getId(),
                order.getOrderNo(),
                order.getSupplierId(),
                order.getSupplierCode(),
                order.getSupplierName(),
                order.getSettlementCompanyId(),
                order.getSettlementCompanyName(),
                false,
                reason,
                0,
                List.of()
        );
    }

    private String validateOrderHeader(PurchaseOrder order) {
        if (!StatusConstants.AUDITED.equals(order.getStatus())) {
            return "只有已审核且未完成采购的采购订单可以导入采购入库";
        }
        if (order.getOrderNo() == null || order.getOrderNo().isBlank()) {
            return "采购订单缺少稳定单号，不能导入采购入库";
        }
        if (order.getSupplierId() == null
                || order.getSupplierCode() == null || order.getSupplierCode().isBlank()
                || order.getSupplierName() == null || order.getSupplierName().isBlank()) {
            return "采购订单缺少稳定供应商身份，不能导入采购入库";
        }
        if (order.getSettlementCompanyId() == null
                || order.getSettlementCompanyName() == null
                || order.getSettlementCompanyName().isBlank()) {
            return "采购订单缺少稳定结算主体身份，不能导入采购入库";
        }
        return null;
    }

    private String validateSourceItem(PurchaseOrderItem item) {
        if (item.getMaterialId() == null || item.getMaterialCode() == null || item.getMaterialCode().isBlank()) {
            return "采购订单第" + item.getLineNo() + "行缺少稳定商品身份";
        }
        if (item.getWarehouseId() == null || item.getWarehouseName() == null || item.getWarehouseName().isBlank()) {
            return "采购订单第" + item.getLineNo() + "行缺少稳定仓库身份";
        }
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            return "采购订单第" + item.getLineNo() + "行数量必须大于0";
        }
        if (item.getPieceWeightTon() == null || item.getPieceWeightTon().signum() < 0) {
            return "采购订单第" + item.getLineNo() + "行件重无效";
        }
        if (item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) {
            return "采购订单第" + item.getLineNo() + "行单价无效";
        }
        return null;
    }

    private List<Long> requireStableSourceItemIds(PurchaseOrder order) {
        List<Long> ids = order.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (ids.size() != order.getItems().size()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购订单存在缺失或重复的来源明细ID");
        }
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购订单没有可导入明细");
        }
        return ids;
    }

    private PurchaseInboundSplitPreviewResponse.Item toPreviewItem(
            PurchaseOrderItem source,
            int remainingQuantity
    ) {
        BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(
                remainingQuantity,
                source.getPieceWeightTon()
        );
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.getUnitPrice());
        return new PurchaseInboundSplitPreviewResponse.Item(
                source.getId(),
                source.getLineNo(),
                source.getMaterialId(),
                source.getMaterialCode(),
                source.getBrand(),
                source.getCategory(),
                source.getMaterial(),
                source.getSpec(),
                source.getLength(),
                source.getUnit(),
                source.getBatchNo(),
                remainingQuantity,
                source.getQuantityUnit(),
                source.getPieceWeightTon(),
                source.getPiecesPerBundle(),
                weightTon,
                source.getUnitPrice(),
                amount
        );
    }

    private PurchaseInboundRequest toCreateRequest(
            PurchaseOrder order,
            PurchaseInboundSplitPreviewResponse.Group group,
            PurchaseInboundImportBatchRequest request
    ) {
        List<PurchaseInboundItemRequest> items = group.items().stream()
                .map(item -> new PurchaseInboundItemRequest(
                        null,
                        item.materialId(),
                        item.materialCode(),
                        item.brand(),
                        item.category(),
                        item.material(),
                        item.spec(),
                        item.length(),
                        item.unit(),
                        item.sourcePurchaseOrderItemId(),
                        group.warehouseId(),
                        group.warehouseName(),
                        group.settlementMode(),
                        item.batchNo(),
                        item.remainingQuantity(),
                        item.quantityUnit(),
                        item.pieceWeightTon(),
                        item.piecesPerBundle(),
                        item.theoreticalWeightTon(),
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        item.unitPrice(),
                        item.amount()
                ))
                .toList();
        return new PurchaseInboundRequest(
                null,
                order.getOrderNo(),
                order.getSupplierId(),
                order.getSupplierCode(),
                order.getSupplierName(),
                group.warehouseId(),
                group.warehouseName(),
                request.inboundDate(),
                group.settlementMode(),
                StatusConstants.DRAFT,
                request.remark(),
                items
        );
    }

    private PurchaseInboundImportBatchResponse.InboundDraft toDraftResponse(PurchaseInbound inbound) {
        return new PurchaseInboundImportBatchResponse.InboundDraft(
                inbound.getId(),
                inbound.getInboundNo(),
                inbound.getWarehouseId(),
                inbound.getWarehouseName(),
                inbound.getSettlementMode(),
                inbound.getItems().size(),
                inbound.getStatus()
        );
    }

    private record GroupKey(Long warehouseId, boolean weighRequired) {
    }

    private static final class MutableGroup {
        private final Long warehouseId;
        private final String warehouseName;
        private final String settlementMode;
        private final List<PurchaseInboundSplitPreviewResponse.Item> items = new ArrayList<>();
        private int totalQuantity;
        private BigDecimal totalWeight = BigDecimal.ZERO;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private MutableGroup(Long warehouseId, String warehouseName, String settlementMode) {
            this.warehouseId = warehouseId;
            this.warehouseName = warehouseName;
            this.settlementMode = settlementMode;
        }

        private void add(PurchaseInboundSplitPreviewResponse.Item item) {
            items.add(item);
            totalQuantity = Math.addExact(totalQuantity, item.remainingQuantity());
            totalWeight = totalWeight.add(item.theoreticalWeightTon());
            totalAmount = totalAmount.add(item.amount());
        }

        private PurchaseInboundSplitPreviewResponse.Group toResponse() {
            return new PurchaseInboundSplitPreviewResponse.Group(
                    warehouseId,
                    warehouseName,
                    settlementMode,
                    totalQuantity,
                    TradeItemCalculator.scaleWeightTon(totalWeight),
                    TradeItemCalculator.scaleAmount(totalAmount),
                    List.copyOf(items)
            );
        }
    }
}
