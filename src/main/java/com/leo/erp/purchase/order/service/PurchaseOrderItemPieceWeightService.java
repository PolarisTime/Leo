package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItemPieceWeight;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemPieceWeightRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    private static final BigDecimal WEIGHT_UNIT = BigDecimal.ONE.movePointLeft(PrecisionConstants.WEIGHT_SCALE);

    private final PurchaseOrderItemPieceWeightRepository repository;
    private final JdbcTemplate jdbc;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

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
            if (unallocatedQuantity <= 0) {
                continue;
            }
            BigDecimal allocatedWeightTon = existingPieces.stream()
                    .filter(piece -> piece.getSalesOrderItemId() != null)
                    .map(piece -> TradeItemCalculator.safeBigDecimal(piece.getWeightTon()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalWeightTon = resolveItemTotalWeight(item);
            BigDecimal unallocatedWeightTon = TradeItemCalculator.scaleWeightTon(
                    totalWeightTon.subtract(allocatedWeightTon)
            );
            if (unallocatedWeightTon.compareTo(BigDecimal.ZERO) < 0) {
                unallocatedWeightTon = BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE);
            }
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

    /**
     * 汇总采购入库的过磅实际重量作为逐件分配的总重量基准。
     * 盘螺/线材等需要过磅结算的品类，实际入库重量与理论重量存在磅差，
     * 应以过磅实际值为准进行逐件分配，否则会导致销售端可分配重量虚高。
     *
     * 若无采购入库（仅有采购订单），则回退使用采购订单的理论重量。
     */
    private BigDecimal resolveItemTotalWeight(PurchaseOrderItem item) {
        BigDecimal theoreticalWeight = TradeItemCalculator.safeBigDecimal(item.getWeightTon());
        List<BigDecimal> weighWeights = jdbc.queryForList("""
                SELECT COALESCE(ini.weigh_weight_ton, ini.weight_ton) AS actual_weight_ton
                  FROM po_purchase_inbound_item ini
                  JOIN po_purchase_inbound inbound ON inbound.id = ini.inbound_id AND inbound.deleted_flag = FALSE
                 WHERE ini.source_purchase_order_item_id = ?
                   AND inbound.deleted_flag = FALSE
                   AND inbound.status IN ('已审核', '完成入库')
                """, BigDecimal.class, item.getId());
        if (weighWeights.isEmpty()) {
            return theoreticalWeight;
        }
        BigDecimal totalWeighWeight = weighWeights.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return TradeItemCalculator.scaleWeightTon(totalWeighWeight);
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
        int quantity = pieceNos == null ? 0 : pieceNos.size();
        if (quantity <= 0) {
            return List.of();
        }
        BigDecimal totalWeightTon = TradeItemCalculator.scaleWeightTon(sourceWeightTon);
        BigDecimal averageWeightTon = TradeItemCalculator.calculateAveragePieceWeightTon(quantity, totalWeightTon);
        BigDecimal averageTotalWeightTon = averageWeightTon.multiply(BigDecimal.valueOf(quantity));
        int residualUnits = totalWeightTon.subtract(averageTotalWeightTon)
                .divide(WEIGHT_UNIT)
                .intValue();
        int residualCount = Math.min(Math.abs(residualUnits), quantity);
        BigDecimal adjustment = residualUnits > 0 ? WEIGHT_UNIT : WEIGHT_UNIT.negate();

        List<PurchaseOrderItemPieceWeight> pieces = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            int pieceNo = pieceNos.get(i);
            BigDecimal pieceWeightTon = averageWeightTon;
            if (residualUnits != 0 && i >= quantity - residualCount) {
                pieceWeightTon = pieceWeightTon.add(adjustment);
            }
            PurchaseOrderItemPieceWeight piece = new PurchaseOrderItemPieceWeight();
            piece.setId(snowflakeIdGenerator.nextId());
            piece.setPurchaseOrderItemId(purchaseOrderItemId);
            piece.setPieceNo(pieceNo);
            piece.setWeightTon(TradeItemCalculator.scaleWeightTon(pieceWeightTon));
            pieces.add(piece);
        }
        return pieces;
    }
}
