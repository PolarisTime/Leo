package com.leo.erp.inventory.web.dto;

/**
 * 库存期初回填结果统计。
 *
 * @param purchaseInCreated  本次新记的采购入库事务数
 * @param salesOutCreated    本次新记的销售出库事务数
 * @param salesReturnCreated 本次新记的销售退货入库事务数
 * @param skipped            已有有效事务或非法明细而跳过的来源明细数
 */
public record InventoryBackfillResponse(
        int purchaseInCreated,
        int salesOutCreated,
        int salesReturnCreated,
        int skipped
) {
}
