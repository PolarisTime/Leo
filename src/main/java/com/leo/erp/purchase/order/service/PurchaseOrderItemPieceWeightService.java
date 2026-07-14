package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItemPieceWeight;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemPieceWeightRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class PurchaseOrderItemPieceWeightService {

    private static final BigDecimal DISPLAY_WEIGHT_UNIT = BigDecimal.ONE.movePointLeft(PrecisionConstants.DISPLAY_WEIGHT_SCALE);

    private final PurchaseOrderItemPieceWeightRepository repository;
    private final JdbcTemplate jdbc;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    public PurchaseOrderItemPieceWeightService(PurchaseOrderItemPieceWeightRepository repository,
                                                JdbcTemplate jdbc,
                                                SnowflakeIdGenerator snowflakeIdGenerator) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.snowflakeIdGenerator = Objects.requireNonNull(snowflakeIdGenerator,
                "SnowflakeIdGenerator must not be null");
    }

    PurchaseOrderItemPieceWeightService(PurchaseOrderItemPieceWeightRepository repository,
                                        JdbcTemplate jdbc) {
        this(repository, jdbc, new SnowflakeIdGenerator(0L));
    }

    @Transactional
    public void regenerateForPurchaseOrderItems(Collection<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> itemIds = items.stream()
                .map(PurchaseOrderItem::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (itemIds.isEmpty()) {
            return;
        }
        List<PurchaseOrderItemPieceWeight> nextPieces = new ArrayList<>();
        for (PurchaseOrderItem item : items) {
            if (item.getId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            List<PurchaseOrderItemPieceWeight> existingPieces = repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(item.getId());
            Set<Integer> allocatedPieceNos = existingPieces.stream()
                    .filter(piece -> piece.getSalesOrderItemId() != null)
                    .map(PurchaseOrderItemPieceWeight::getPieceNo)
                    .collect(Collectors.toCollection(HashSet::new));
            int unallocatedQuantity = item.getQuantity() - allocatedPieceNos.size();
            BigDecimal allocatedWeightTon = existingPieces.stream()
                    .filter(piece -> piece.getSalesOrderItemId() != null)
                    .map(piece -> TradeItemCalculator.safeBigDecimal(piece.getWeightTon()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalWeightTon = resolveItemTotalWeight(item);
            rejectRegenerateWhenAllocatedWeightExceedsTarget(item.getId(), allocatedWeightTon, totalWeightTon);
            if (unallocatedQuantity <= 0) {
                rejectRegenerateWhenAllocatedWeightDiffersFromTarget(item.getId(), allocatedWeightTon, totalWeightTon);
                continue;
            }
            BigDecimal unallocatedWeightTon = TradeItemCalculator.scaleWeightTon(
                    totalWeightTon.subtract(allocatedWeightTon)
            );
            List<Integer> unallocatedPieceNos = IntStream.rangeClosed(1, item.getQuantity())
                    .filter(pieceNo -> !allocatedPieceNos.contains(pieceNo))
                    .limit(unallocatedQuantity)
                    .boxed()
                    .toList();
            repository.deleteUnallocatedByPurchaseOrderItemIdIn(List.of(item.getId()));
            nextPieces.addAll(buildPieceWeights(item.getId(), unallocatedPieceNos, unallocatedWeightTon));
        }
        if (!nextPieces.isEmpty()) {
            repository.saveAll(nextPieces);
        }
    }

    private void rejectRegenerateWhenAllocatedWeightExceedsTarget(Long purchaseOrderItemId,
                                                                  BigDecimal allocatedWeightTon,
                                                                  BigDecimal targetWeightTon) {
        BigDecimal scaledAllocatedWeightTon = TradeItemCalculator.scaleWeightTon(allocatedWeightTon);
        BigDecimal scaledTargetWeightTon = TradeItemCalculator.scaleWeightTon(targetWeightTon);
        if (scaledAllocatedWeightTon.compareTo(scaledTargetWeightTon) <= 0) {
            return;
        }
        BigDecimal overWeightTon = TradeItemCalculator.scaleWeightTon(
                scaledAllocatedWeightTon.subtract(scaledTargetWeightTon)
        );
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "采购明细" + purchaseOrderItemId
                        + "已分配重量 " + scaledAllocatedWeightTon
                        + " 大于目标重量 " + scaledTargetWeightTon
                        + "，差额 " + overWeightTon
                        + "，不能自动重建逐件重量。请先反审核销售出库/销售订单后再重新入库"
        );
    }

    private void rejectRegenerateWhenAllocatedWeightDiffersFromTarget(Long purchaseOrderItemId,
                                                                      BigDecimal allocatedWeightTon,
                                                                      BigDecimal targetWeightTon) {
        BigDecimal scaledAllocatedWeightTon = TradeItemCalculator.scaleWeightTon(allocatedWeightTon);
        BigDecimal scaledTargetWeightTon = TradeItemCalculator.scaleWeightTon(targetWeightTon);
        if (scaledAllocatedWeightTon.compareTo(scaledTargetWeightTon) == 0) {
            return;
        }
        BigDecimal differenceWeightTon = TradeItemCalculator.scaleWeightTon(
                scaledTargetWeightTon.subtract(scaledAllocatedWeightTon)
        );
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "采购明细" + purchaseOrderItemId
                        + "已全部分配，已分配重量 " + scaledAllocatedWeightTon
                        + " 与目标重量 " + scaledTargetWeightTon
                        + " 不一致，差额 " + differenceWeightTon
                        + "，不能自动重建逐件重量。请先反审核销售出库/销售订单后再重新入库"
        );
    }

    @Transactional
    public void rebalanceForPurchaseOrderItems(Collection<PurchaseOrderItem> items,
                                               Collection<Long> lockedSalesOrderItemIds) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<Long> lockedIds = lockedSalesOrderItemIds == null
                ? Set.of()
                : lockedSalesOrderItemIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<PurchaseOrderItemPieceWeight> nextPieces = new ArrayList<>();
        for (PurchaseOrderItem item : items) {
            if (item.getId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            List<PurchaseOrderItemPieceWeight> existingPieces =
                    repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(item.getId());
            List<PurchaseOrderItemPieceWeight> lockedPieces = existingPieces.stream()
                    .filter(piece -> piece.getSalesOrderItemId() != null)
                    .filter(piece -> lockedIds.contains(piece.getSalesOrderItemId()))
                    .toList();
            BigDecimal lockedWeightTon = lockedPieces.stream()
                    .map(piece -> TradeItemCalculator.safeBigDecimal(piece.getWeightTon()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal targetWeightTon = resolveItemTotalWeight(item);
            if (targetWeightTon.compareTo(lockedWeightTon) < 0) {
                BigDecimal scaledLockedWeightTon = TradeItemCalculator.scaleWeightTon(lockedWeightTon);
                BigDecimal scaledTargetWeightTon = TradeItemCalculator.scaleWeightTon(targetWeightTon);
                BigDecimal overWeightTon = TradeItemCalculator.scaleWeightTon(
                        scaledLockedWeightTon.subtract(scaledTargetWeightTon)
                );
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "采购明细" + item.getId()
                                + "锁定重量 " + scaledLockedWeightTon
                                + " 大于采购明细目标重量 " + scaledTargetWeightTon
                                + "，差额 " + overWeightTon
                                + "，不能自动重平衡。请先反审核销售出库/销售订单，或保持销售出库为预出库后再做采购入库"
                );
            }

            List<PieceSlot> rebalanceSlots = rebalanceSlots(item, existingPieces, lockedIds);
            if (rebalanceSlots.isEmpty()) {
                continue;
            }
            BigDecimal rebalanceWeightTon = TradeItemCalculator.scaleWeightTon(
                    targetWeightTon.subtract(lockedWeightTon)
            );
            repository.deleteUnallocatedByPurchaseOrderItemIdIn(List.of(item.getId()));
            nextPieces.addAll(buildPieceWeightsForSlots(item.getId(), rebalanceSlots, rebalanceWeightTon));
        }
        if (!nextPieces.isEmpty()) {
            repository.saveAll(nextPieces);
        }
    }

    /**
     * 已入库部分使用有效入库实际重量，未入库部分继续使用采购计划件重。
     */
    private BigDecimal resolveItemTotalWeight(PurchaseOrderItem item) {
        BigDecimal theoreticalWeight = TradeItemCalculator.safeBigDecimal(item.getWeightTon());
        List<EffectiveInboundWeight> inboundWeights = jdbc.query("""
                SELECT ini.quantity,
                       COALESCE(ini.weigh_weight_ton, ini.weight_ton) AS actual_weight_ton
                  FROM po_purchase_inbound_item ini
                  JOIN po_purchase_inbound inbound ON inbound.id = ini.inbound_id AND inbound.deleted_flag = FALSE
                 WHERE ini.source_purchase_order_item_id = ?
                   AND inbound.deleted_flag = FALSE
                   AND inbound.status IN ('已审核', '完成入库')
                """, (rs, rowNum) -> new EffectiveInboundWeight(
                rs.getInt("quantity"),
                rs.getBigDecimal("actual_weight_ton")
        ), item.getId());
        if (inboundWeights.isEmpty()) {
            return theoreticalWeight;
        }
        int receivedQuantity = inboundWeights.stream()
                .mapToInt(EffectiveInboundWeight::quantity)
                .sum();
        BigDecimal receivedWeight = inboundWeights.stream()
                .map(EffectiveInboundWeight::weightTon)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int remainingQuantity = Math.max(0, item.getQuantity() - receivedQuantity);
        BigDecimal remainingPlannedWeight = TradeItemCalculator.calculateWeightTon(
                remainingQuantity,
                item.getPieceWeightTon()
        );
        return TradeItemCalculator.scaleWeightTon(receivedWeight.add(remainingPlannedWeight));
    }

    @Transactional
    public BigDecimal allocateForSalesOrderItem(PurchaseOrderItem sourceItem,
                                                Integer quantity,
                                                Long salesOrderItemId,
                                                int lineNo) {
        if (sourceItem == null || sourceItem.getId() == null || quantity == null || quantity <= 0) {
            return BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE);
        }
        if (salesOrderItemId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行销售订单明细ID缺失，无法分配逐件重量");
        }
        repository.releaseBySalesOrderItemIdIn(List.of(salesOrderItemId));
        ensureGenerated(sourceItem);
        List<PurchaseOrderItemPieceWeight> availablePieces =
                repository.findAvailableByPurchaseOrderItemIdForUpdate(sourceItem.getId());
        if (availablePieces.size() < quantity) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行采购订单可用件数不足");
        }
        BigDecimal weightTon = BigDecimal.ZERO;
        for (int i = 0; i < quantity; i++) {
            PurchaseOrderItemPieceWeight piece = availablePieces.get(i);
            piece.setSalesOrderItemId(salesOrderItemId);
            weightTon = weightTon.add(TradeItemCalculator.safeBigDecimal(piece.getWeightTon()));
        }
        repository.saveAll(availablePieces.subList(0, quantity));
        return TradeItemCalculator.scaleWeightTon(weightTon);
    }

    @Transactional
    public void releaseSalesOrderItems(Collection<Long> salesOrderItemIds) {
        if (salesOrderItemIds == null || salesOrderItemIds.isEmpty()) {
            return;
        }
        List<Long> filteredIds = salesOrderItemIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (filteredIds.isEmpty()) {
            return;
        }
        repository.releaseBySalesOrderItemIdIn(filteredIds);
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> summarizeRemainingWeightByPurchaseOrderItemIds(Collection<Long> purchaseOrderItemIds) {
        if (purchaseOrderItemIds == null || purchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        return repository.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds)
                .stream()
                .collect(Collectors.toMap(
                        PurchaseOrderItemPieceWeightRepository.RemainingWeightSummary::getPurchaseOrderItemId,
                        summary -> TradeItemCalculator.scaleWeightTon(summary.getTotalWeightTon())
                ));
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> summarizeLockedSalesWeightByPurchaseOrderItemIds(Collection<Long> purchaseOrderItemIds) {
        if (purchaseOrderItemIds == null || purchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        return repository.summarizeLockedSalesWeightByPurchaseOrderItemIds(
                        purchaseOrderItemIds,
                        StatusConstants.SALES_COMPLETED,
                        StatusConstants.AUDITED,
                        StatusConstants.AUDITED
                )
                .stream()
                .collect(Collectors.toMap(
                        PurchaseOrderItemPieceWeightRepository.PurchaseOrderItemWeightSummary::getPurchaseOrderItemId,
                        summary -> TradeItemCalculator.scaleWeightTon(summary.getTotalWeightTon())
                ));
    }

    private void ensureGenerated(PurchaseOrderItem sourceItem) {
        if (sourceItem == null || sourceItem.getId() == null) {
            return;
        }
        if (!repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(sourceItem.getId()).isEmpty()) {
            return;
        }
        if (sourceItem.getQuantity() == null || sourceItem.getQuantity() <= 0) {
            return;
        }
        List<Integer> pieceNos = IntStream.rangeClosed(1, sourceItem.getQuantity())
                .boxed()
                .toList();
        repository.saveAll(buildPieceWeights(sourceItem.getId(), pieceNos, sourceItem.getWeightTon()));
    }

    private List<PurchaseOrderItemPieceWeight> buildPieceWeights(Long purchaseOrderItemId,
                                                                 List<Integer> pieceNos,
                                                                 BigDecimal sourceWeightTon) {
        if (pieceNos == null) {
            return List.of();
        }
        return buildPieceWeightsForSlots(
                purchaseOrderItemId,
                pieceNos.stream().map(pieceNo -> new PieceSlot(null, pieceNo, null)).toList(),
                sourceWeightTon
        );
    }

    private List<PurchaseOrderItemPieceWeight> buildPieceWeightsForSlots(Long purchaseOrderItemId,
                                                                         List<PieceSlot> slots,
                                                                         BigDecimal sourceWeightTon) {
        int quantity = slots == null ? 0 : slots.size();
        if (quantity <= 0) {
            return List.of();
        }
        List<BigDecimal> pieceWeights = splitWeightByDisplayUnit(sourceWeightTon, quantity);

        List<PurchaseOrderItemPieceWeight> pieces = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            PieceSlot slot = slots.get(i);
            PurchaseOrderItemPieceWeight piece = new PurchaseOrderItemPieceWeight();
            piece.setId(slot.id() == null ? snowflakeIdGenerator.nextId() : slot.id());
            piece.setPurchaseOrderItemId(purchaseOrderItemId);
            piece.setPieceNo(slot.pieceNo());
            piece.setSalesOrderItemId(slot.salesOrderItemId());
            piece.setWeightTon(TradeItemCalculator.scaleWeightTon(pieceWeights.get(i)));
            pieces.add(piece);
        }
        return pieces;
    }

    private List<BigDecimal> splitWeightByDisplayUnit(BigDecimal sourceWeightTon, int quantity) {
        if (quantity <= 0) {
            return List.of();
        }
        BigDecimal totalWeightTon = requireDisplayScaleWeight(sourceWeightTon);
        BigInteger totalUnits = totalWeightTon
                .movePointRight(PrecisionConstants.DISPLAY_WEIGHT_SCALE)
                .toBigIntegerExact();
        BigInteger[] quotientAndRemainder = totalUnits.divideAndRemainder(BigInteger.valueOf(quantity));
        BigInteger baseUnits = quotientAndRemainder[0];
        int residualCount = quotientAndRemainder[1].intValueExact();

        List<BigDecimal> weights = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            BigInteger pieceUnits = i < residualCount ? baseUnits.add(BigInteger.ONE) : baseUnits;
            weights.add(new BigDecimal(pieceUnits).multiply(DISPLAY_WEIGHT_UNIT));
        }
        assertSplitTotal(totalWeightTon, weights);
        return weights;
    }

    private BigDecimal requireDisplayScaleWeight(BigDecimal sourceWeightTon) {
        BigDecimal safeWeightTon = TradeItemCalculator.safeBigDecimal(sourceWeightTon);
        try {
            return safeWeightTon.setScale(PrecisionConstants.DISPLAY_WEIGHT_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购逐件重量分配要求明细重量最多保留" + PrecisionConstants.DISPLAY_WEIGHT_SCALE + "位小数"
            );
        }
    }

    private void assertSplitTotal(BigDecimal totalWeightTon, List<BigDecimal> weights) {
        BigDecimal splitTotalWeightTon = weights.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(PrecisionConstants.DISPLAY_WEIGHT_SCALE, RoundingMode.UNNECESSARY);
        if (splitTotalWeightTon.compareTo(totalWeightTon) != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购逐件重量分配合计不等于明细重量");
        }
    }

    private List<PieceSlot> rebalanceSlots(PurchaseOrderItem item,
                                           List<PurchaseOrderItemPieceWeight> existingPieces,
                                           Set<Long> lockedIds) {
        Map<Integer, PurchaseOrderItemPieceWeight> existingByPieceNo = existingPieces.stream()
                .filter(piece -> piece.getPieceNo() != null)
                .collect(Collectors.toMap(
                        PurchaseOrderItemPieceWeight::getPieceNo,
                        piece -> piece,
                        (left, ignored) -> left
                ));
        List<PieceSlot> slots = new ArrayList<>();
        for (int pieceNo = 1; pieceNo <= item.getQuantity(); pieceNo++) {
            PurchaseOrderItemPieceWeight existing = existingByPieceNo.get(pieceNo);
            if (existing != null
                    && existing.getSalesOrderItemId() != null
                    && lockedIds.contains(existing.getSalesOrderItemId())) {
                continue;
            }
            slots.add(new PieceSlot(
                    existing == null ? null : existing.getId(),
                    pieceNo,
                    existing == null ? null : existing.getSalesOrderItemId()
            ));
        }
        return slots;
    }

    private record PieceSlot(Long id, Integer pieceNo, Long salesOrderItemId) {
    }

    private record EffectiveInboundWeight(int quantity, BigDecimal weightTon) {
    }
}
