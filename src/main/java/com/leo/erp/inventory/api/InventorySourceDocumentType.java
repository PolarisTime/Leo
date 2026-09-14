package com.leo.erp.inventory.api;

/**
 * 库存事务来源单据类型。值直接落库到 {@code inv_transaction.source_document_type}。
 */
public enum InventorySourceDocumentType {

    /** 采购入库单。 */
    PURCHASE_INBOUND,

    /** 销售出库单。 */
    SALES_OUTBOUND,

    /** 销售退货单（反向入库）。 */
    SALES_RETURN
}
