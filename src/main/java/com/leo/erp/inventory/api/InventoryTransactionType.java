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
    PURCHASE_RETURN_OUT(-1),

    /** 调拨：方向由调拨的出/入库方向决定，不使用类型固定方向。 */
    TRANSFER(0),

    /** 盘点调整：方向由盘盈/盘亏决定，不使用类型固定方向。 */
    COUNT_ADJUST(0);

    private final short direction;

    InventoryTransactionType(int direction) {
        this.direction = (short) direction;
    }

    /**
     * 类型的固定库存方向。
     *
     * @throws IllegalStateException 类型无固定方向（TRANSFER/COUNT_ADJUST）时抛出，
     *         调用方必须在事务行上显式指定方向，禁止落库 direction=0。
     */
    public short direction() {
        if (direction == 0) {
            throw new IllegalStateException(name() + " 无固定库存方向，必须按事务行显式指定");
        }
        return direction;
    }
}
