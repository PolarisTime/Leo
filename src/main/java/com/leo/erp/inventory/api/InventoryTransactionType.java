package com.leo.erp.inventory.api;

/**
 * 库存事务类型及库存增减方向。值直接落库到 {@code inv_transaction.transaction_type}。
 */
public enum InventoryTransactionType {

    /** 采购入库：增加库存。 */
    PURCHASE_IN(1),

    /** 销售出库：减少库存。 */
    SALES_OUT(-1),

    /** 销售退货入库：增加库存。 */
    SALES_RETURN_IN(1),

    /** 采购退货出库：减少库存（预留，后续实现）。 */
    PURCHASE_RETURN_OUT(-1);

    private final short direction;

    InventoryTransactionType(int direction) {
        this.direction = (short) direction;
    }

    public short direction() {
        return direction;
    }
}
