package com.leo.erp.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.inventory.repository.InventoryBalanceSnapshotRepository;
import com.leo.erp.inventory.web.dto.InventoryBalanceMismatchResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 库存余额快照运维：从账本重建快照、守恒校验。
 *
 * <p>重建是幂等的（按账本聚合覆盖写回 + 清理过期维度），但<b>不是</b>可以随时执行的无代价操作：
 * 它在单事务内持有 {@code inv:backfill} 全局排他锁，与所有库存记账事务（持有同一键的事务级
 * 共享锁）互斥——重建拿到锁之前必须等所有在途记账提交，期间新的记账会阻塞等待。
 * 因此重建会短暂阻塞库存写路径，应在业务低峰执行；「可随时重复调用」的说法不成立。</p>
 *
 * <p>校验结果为仓库内部类型 {@link InventoryBalanceSnapshotRepository.BalanceMismatch}，
 * 对外一律经 {@link #reconcile(PageQuery)} 映射成 {@link InventoryBalanceMismatchResponse}，
 * 不得把 repository 内部 record 暴露给 controller/HTTP 契约。</p>
 */
@Service
public class InventoryBalanceMaintenanceService {

    /** 允许的排序字段 → SQL 列（常量白名单，禁止把请求参数拼进 SQL）。 */
    private static final Map<String, String> MISMATCH_SORT_COLUMNS = Map.of(
            "materialId", "material_id",
            "warehouseId", "warehouse_id",
            "ledgerQuantity", "ledger_quantity",
            "snapshotQuantity", "snapshot_quantity",
            "ledgerAmount", "ledger_amount",
            "snapshotAmount", "snapshot_amount"
    );

    private static final String MISMATCH_TIE_BREAKER = ", material_id ASC, warehouse_id ASC";

    /** 默认稳定排序：按维度主键升序（与 inv_balance 主键顺序一致）。 */
    private static final String MISMATCH_DEFAULT_ORDER = " ORDER BY material_id ASC, warehouse_id ASC";

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
        lockService.lockBackfillTxScoped();
        return snapshotRepository.rebuild();
    }

    /**
     * 校验 Σ账本 == 快照，返回不一致维度；空列表表示守恒。
     *
     * <p>保留非分页入口供诊断/测试直接调用；HTTP 列表接口走 {@link #reconcile(PageQuery)}。</p>
     */
    @Transactional(readOnly = true)
    public List<InventoryBalanceSnapshotRepository.BalanceMismatch> reconcile() {
        return snapshotRepository.findMismatches();
    }

    /**
     * 分页校验 Σ账本 == 快照。
     *
     * <p>结果按 {@code (material_id, warehouse_id)} 兜底排序，保证翻页稳定；
     * {@code sortBy} 只接受本类 {@link #MISMATCH_SORT_COLUMNS} 白名单内的字段（由
     * {@code PageSortFieldCatalog} 先做一次校验），未知取值退回默认排序。</p>
     */
    @Transactional(readOnly = true)
    public PageResponse<InventoryBalanceMismatchResponse> reconcile(PageQuery query) {
        long totalElements = snapshotRepository.countMismatches();
        List<InventoryBalanceMismatchResponse> content = totalElements == 0
                ? List.of()
                : snapshotRepository
                        .findMismatches(mismatchOrderBy(query), query.size(), (long) query.page() * query.size())
                        .stream()
                        .map(InventoryBalanceMaintenanceService::toResponse)
                        .toList();
        int totalPages = query.size() <= 0 ? 0 : (int) ((totalElements + query.size() - 1) / query.size());
        boolean hasMore = (long) (query.page() + 1) * query.size() < totalElements;
        return new PageResponse<>(content, totalElements, totalPages, query.page(), query.size(), hasMore);
    }

    private String mismatchOrderBy(PageQuery query) {
        String expression = query.sortBy() == null ? null : MISMATCH_SORT_COLUMNS.get(query.sortBy());
        if (expression == null) {
            return MISMATCH_DEFAULT_ORDER;
        }
        String direction = "asc".equalsIgnoreCase(query.direction()) ? "ASC" : "DESC";
        return " ORDER BY " + expression + " " + direction + MISMATCH_TIE_BREAKER;
    }

    private static InventoryBalanceMismatchResponse toResponse(
            InventoryBalanceSnapshotRepository.BalanceMismatch mismatch) {
        return new InventoryBalanceMismatchResponse(
                mismatch.materialId(),
                mismatch.warehouseId(),
                mismatch.ledgerQuantity(),
                mismatch.snapshotQuantity(),
                mismatch.ledgerAmount(),
                mismatch.snapshotAmount()
        );
    }
}
