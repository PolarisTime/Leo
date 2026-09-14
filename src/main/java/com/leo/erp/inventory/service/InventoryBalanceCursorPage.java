package com.leo.erp.inventory.service;

import com.leo.erp.inventory.web.dto.InventoryBalanceResponse;

import java.util.List;

/**
 * 库存余额 keyset 游标分页结果。
 *
 * @param content        当前页内容
 * @param hasMore        是否还有下一页
 * @param nextMaterialId 下一页游标 material_id，null 表示已到末页
 * @param nextWarehouseId 下一页游标 warehouse_id
 */
public record InventoryBalanceCursorPage(
        List<InventoryBalanceResponse> content,
        boolean hasMore,
        Long nextMaterialId,
        Long nextWarehouseId
) {
}
