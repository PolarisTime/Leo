package com.leo.erp.inventory.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.inventory.api.InventoryTransactionCommand;
import com.leo.erp.inventory.api.InventoryTransactionInput;
import com.leo.erp.inventory.api.InventoryTransactionType;
import com.leo.erp.inventory.domain.entity.InventoryTransaction;
import com.leo.erp.inventory.repository.InventoryTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 库存事务记账服务。
 *
 * <p>成本采用移动加权平均：出库/退货入账前读取 (material, warehouse) 当前余额
 * （{@code Σ signed qty} / {@code Σ signed amount}）得到单位成本；
 * 库内存量不足时不拒绝，按当前均价并记录 warn；无存量时用来源单价兜底。
 *
 * <p>幂等：同一来源明细同一事务类型在未删除状态下只记一次。反审核/删除软删事务。
 * 记账在同一事务内完成，读取余额前对库存维度加 PostgreSQL 事务级咨询锁防并发。
 */
@Service
public class InventoryTransactionService implements InventoryTransactionCommand {

    private static final Logger log = LoggerFactory.getLogger(InventoryTransactionService.class);
    private static final int COST_SCALE = 2;

    private final InventoryTransactionRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final InventoryTransactionLockService lockService;
    private final InventoryBalanceReader balanceReader;

    public InventoryTransactionService(InventoryTransactionRepository repository,
                                       SnowflakeIdGenerator idGenerator,
                                       InventoryTransactionLockService lockService,
                                       InventoryBalanceReader balanceReader) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.lockService = lockService;
        this.balanceReader = balanceReader;
    }

    @Override
    @Transactional
    public void recordPurchaseIn(InventoryTransactionInput input) {
        record(input, InventoryTransactionType.PURCHASE_IN);
    }

    @Override
    @Transactional
    public void recordSalesOut(InventoryTransactionInput input) {
        record(input, InventoryTransactionType.SALES_OUT);
    }

    @Override
    @Transactional
    public void recordSalesReturnIn(InventoryTransactionInput input) {
        record(input, InventoryTransactionType.SALES_RETURN_IN);
    }

    @Override
    @Transactional
    public void softDeleteBySource(String sourceDocumentType, Long sourceDocumentId) {
        if (sourceDocumentType == null || sourceDocumentId == null) {
            return;
        }
        List<InventoryTransaction> active = repository
                .findBySourceDocumentTypeAndSourceDocumentIdAndDeletedFlagFalse(sourceDocumentType, sourceDocumentId);
        if (active.isEmpty()) {
            return;
        }
        active.forEach(transaction -> transaction.setDeletedFlag(true));
        repository.saveAll(active);
        repository.flush();
    }

    private void record(InventoryTransactionInput input, InventoryTransactionType type) {
        if (input == null || input.lines() == null || input.lines().isEmpty()) {
            return;
        }
        List<InventoryTransactionInput.Line> lines = input.lines().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing((InventoryTransactionInput.Line line) -> sortKey(line.materialId()))
                        .thenComparing(line -> sortKey(resolveWarehouseId(line, input))))
                .toList();
        // 统一锁顺序：同一事务内先按 (materialId, warehouseId) 升序一次性获取本单全部维度锁，
        // 与期初回填的批次加锁协议一致，避免 AB-BA 死锁。
        lockService.lockAll(lines.stream()
                .filter(line -> line.materialId() != null && line.quantity() > 0)
                .map(line -> new InventoryTransactionLockService.Dimension(
                        line.materialId(), resolveWarehouseId(line, input)))
                .toList());
        for (InventoryTransactionInput.Line line : lines) {
            if (line.materialId() == null || line.quantity() <= 0) {
                log.warn("跳过非法库存明细行: materialId={}, quantity={}", line.materialId(), line.quantity());
                continue;
            }
            if (repository.existsBySourceDocumentTypeAndSourceItemIdAndTransactionTypeAndDeletedFlagFalse(
                    input.sourceDocumentType(), line.sourceItemId(), type.name())) {
                continue;
            }
            Long warehouseId = resolveWarehouseId(line, input);
            String warehouseName = line.warehouseName() != null
                    ? line.warehouseName()
                    : input.defaultWarehouseName();
            BigDecimal unitCost = resolveUnitCost(type, line, warehouseId);
            BigDecimal amount = BigDecimal.valueOf(type.direction())
                    .multiply(unitCost)
                    .multiply(BigDecimal.valueOf(line.quantity()))
                    .setScale(COST_SCALE, RoundingMode.HALF_UP);
            repository.save(buildTransaction(input, type, line, warehouseId, warehouseName, unitCost, amount));
            repository.flush();
        }
    }

    private static Long resolveWarehouseId(InventoryTransactionInput.Line line, InventoryTransactionInput input) {
        return line.warehouseId() != null ? line.warehouseId() : input.defaultWarehouseId();
    }

    private InventoryTransaction buildTransaction(InventoryTransactionInput input,
                                                  InventoryTransactionType type,
                                                  InventoryTransactionInput.Line line,
                                                  Long warehouseId,
                                                  String warehouseName,
                                                  BigDecimal unitCost,
                                                  BigDecimal amount) {
        InventoryTransaction entity = new InventoryTransaction();
        long id = idGenerator.nextId();
        entity.setId(id);
        entity.setTransactionNo(String.valueOf(id));
        entity.setTransactionType(type.name());
        entity.setMaterialId(line.materialId());
        entity.setMaterialCode(line.materialCode());
        entity.setWarehouseId(warehouseId);
        entity.setWarehouseName(warehouseName);
        entity.setBatchNo(line.batchNo());
        entity.setDirection(type.direction());
        entity.setQuantity(line.quantity());
        entity.setQuantityUnit(line.quantityUnit());
        entity.setUnitCost(unitCost);
        entity.setAmount(amount);
        entity.setSourceDocumentType(input.sourceDocumentType());
        entity.setSourceDocumentId(input.sourceDocumentId());
        entity.setSourceDocumentNo(input.sourceDocumentNo());
        entity.setSourceItemId(line.sourceItemId());
        entity.setOccurredAt(input.occurredAt());
        return entity;
    }

    private BigDecimal resolveUnitCost(InventoryTransactionType type,
                                       InventoryTransactionInput.Line line,
                                       Long warehouseId) {
        BigDecimal sourcePrice = line.sourceUnitPrice() == null
                ? BigDecimal.ZERO
                : line.sourceUnitPrice();
        if (type == InventoryTransactionType.PURCHASE_IN) {
            return sourcePrice.setScale(COST_SCALE, RoundingMode.HALF_UP);
        }
        InventoryBalanceTotals totals = balanceReader.currentBalance(line.materialId(), warehouseId);
        if (totals.quantity() > 0) {
            if (type == InventoryTransactionType.SALES_OUT && totals.quantity() < line.quantity()) {
                log.warn("库存存量不足，按当前移动平均成本出库: materialId={}, warehouseId={}, balanceQty={}, outQty={}",
                        line.materialId(), warehouseId, totals.quantity(), line.quantity());
            }
            return totals.amount().divide(
                    BigDecimal.valueOf(totals.quantity()), COST_SCALE, RoundingMode.HALF_UP);
        }
        if (type == InventoryTransactionType.SALES_OUT) {
            log.warn("库存无存量，销售出库成本按来源单价兜底: materialId={}, warehouseId={}",
                    line.materialId(), warehouseId);
        }
        return sourcePrice.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }

    private static long sortKey(Long value) {
        return value == null ? Long.MAX_VALUE : value;
    }
}
