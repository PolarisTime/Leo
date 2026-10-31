package com.leo.erp.inventory.web.dto;

/**
 * 库存余额重建结果。
 *
 * @param rebuiltRows 本次按账本聚合覆盖写入的快照维度行数。重建是幂等的（覆盖写回 + 清理过期维度），
 *                    重复调用只在账本变化时才会得到不同的行数。
 */
public record InventoryBalanceRebuildResponse(
        int rebuiltRows
) {
}
