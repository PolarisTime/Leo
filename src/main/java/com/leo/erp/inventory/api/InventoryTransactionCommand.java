package com.leo.erp.inventory.api;

/**
 * 库存事务记账端口：由采购入库、销售出库、销售退货工作流在审核/反审核/删除时调用。
 *
 * <p>记账幂等：同一来源明细同一事务类型在未删除状态下只会存在一条事务。
 * 反审核/删除通过 {@link #softDeleteBySource(String, Long)} 软删该来源单据的全部事务，保持余额正确。
 */
public interface InventoryTransactionCommand {

    /**
     * 采购入库审核：按来源单价记增加库存。
     */
    void recordPurchaseIn(InventoryTransactionInput input);

    /**
     * 销售出库审核：按当前移动加权平均成本记减少库存。
     */
    void recordSalesOut(InventoryTransactionInput input);

    /**
     * 销售退货审核：按当前移动加权平均成本（无存量时用来源单价）记增加库存。
     */
    void recordSalesReturnIn(InventoryTransactionInput input);

    /**
     * 反审核/删除来源单据时软删其全部库存事务；重复调用幂等。
     *
     * @param sourceDocumentType 来源单据类型
     * @param sourceDocumentId   来源单据ID
     */
    void softDeleteBySource(String sourceDocumentType, Long sourceDocumentId);
}
