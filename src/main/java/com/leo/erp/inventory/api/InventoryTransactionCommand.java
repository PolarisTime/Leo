package com.leo.erp.inventory.api;

/**
 * 库存事务记账端口：由采购入库、销售出库、销售退货工作流在审核/反审核/删除时调用。
 *
 * <p>记账幂等：同一来源明细同一事务类型在未删除状态下只会存在一条事务。
 * 反审核/删除通过 {@link #softDeleteBySource(String, Long)} 软删该来源单据的全部事务，保持余额正确。
 *
 * <p>业务侧不允许负库存：{@link #recordSalesOut} 在可用量不足时拒绝并回滚整单；
 * 仅 {@link #recordBackfill} 这一历史补记入口豁免可用量校验。
 */
public interface InventoryTransactionCommand {

    /**
     * 采购入库审核：按来源单价记增加库存。
     */
    void recordPurchaseIn(InventoryTransactionInput input);

    /**
     * 销售出库审核：按当前移动加权平均成本记减少库存；可用量不足时拒绝（业务侧不允许负库存）。
     */
    void recordSalesOut(InventoryTransactionInput input);

    /**
     * 销售退货审核：按当前移动加权平均成本（无存量时用来源单价）记增加库存。
     */
    void recordSalesReturnIn(InventoryTransactionInput input);

    /**
     * 期初回填补记：按业务日期升序把历史已审核单据补记为库存事务。
     *
     * <p>与常规记账的唯一区别是<b>不做可用量校验</b>：历史单据存在「出库业务日期早于入库」
     * 的既成事实（整体净额非负），若逐单严格校验会让整批回填失败并回滚，历史缺口永远补不上。
     * 回填完成后应复核是否仍存在负余额，若有需人工核对来源单据。</p>
     *
     * @param input 来源单据的库存输入
     * @param type  事务类型（PURCHASE_IN / SALES_OUT / SALES_RETURN_IN）
     */
    void recordBackfill(InventoryTransactionInput input, InventoryTransactionType type);

    /**
     * 反审核/删除来源单据时软删其全部库存事务；重复调用幂等。
     *
     * @param sourceDocumentType 来源单据类型
     * @param sourceDocumentId   来源单据ID
     */
    void softDeleteBySource(String sourceDocumentType, Long sourceDocumentId);
}
