package com.leo.erp.inventory.service;

import com.leo.erp.inventory.repository.InventoryBalanceSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 库存余额快照运维：从账本重建快照、守恒校验。
 *
 * <p>重建是幂等的（按账本聚合覆盖写回 + 清理过期维度），可在任何怀疑快照漂移时重复调用；
 * 与期初回填共用 {@code inv:backfill} 全局串行锁，避免并发重建互相覆盖。
 */
@Service
public class InventoryBalanceMaintenanceService {

    private final InventoryBalanceSnapshotRepository snapshotRepository;
    private final InventoryTransactionLockService lockService;

    public InventoryBalanceMaintenanceService(InventoryBalanceSnapshotRepository snapshotRepository,
                                              InventoryTransactionLockService lockService) {
        this.snapshotRepository = snapshotRepository;
        this.lockService = lockService;
    }

    /**
     * 从 inv_transaction 账本幂等重算 inv_balance。
     *
     * @return 覆盖写入的快照维度行数
     */
    @Transactional
    public int rebuild() {
        lockService.lockBackfill();
        return snapshotRepository.rebuild();
    }

    /**
     * 校验 Σ账本 == 快照，返回不一致维度；空列表表示守恒。
     */
    @Transactional(readOnly = true)
    public List<InventoryBalanceSnapshotRepository.BalanceMismatch> reconcile() {
        return snapshotRepository.findMismatches();
    }
}
