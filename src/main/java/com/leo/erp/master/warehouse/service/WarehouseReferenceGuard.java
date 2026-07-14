package com.leo.erp.master.warehouse.service;

import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;

import java.util.ArrayList;
import java.util.List;

final class WarehouseReferenceGuard {

    private final MasterDataReferenceGuard referenceGuard;

    WarehouseReferenceGuard(MasterDataReferenceGuard referenceGuard) {
        this.referenceGuard = referenceGuard;
    }

    void assertNoReferences(Warehouse entity) {
        if (referenceGuard == null) {
            return;
        }
        referenceGuard.assertNoReferences("该仓库", warehouseReferences(entity));
    }

    private List<ReferenceCheck> warehouseReferences(Warehouse entity) {
        List<ReferenceCheck> references = new ArrayList<>(stableIdentityReferences(entity.getId()));
        references.addAll(legacyNameReferences(entity.getWarehouseName()));
        return List.copyOf(references);
    }

    private List<ReferenceCheck> stableIdentityReferences(Long warehouseId) {
        return List.of(
                ReferenceCheck.ofActiveParent(
                        "po_purchase_order_item", "warehouse_id", warehouseId,
                        "po_purchase_order", "order_id"
                ),
                ReferenceCheck.active("po_purchase_inbound", "warehouse_id", warehouseId),
                ReferenceCheck.ofActiveParent(
                        "po_purchase_inbound_item", "warehouse_id", warehouseId,
                        "po_purchase_inbound", "inbound_id"
                ),
                ReferenceCheck.ofActiveParent(
                        "so_sales_order_item", "warehouse_id", warehouseId,
                        "so_sales_order", "order_id"
                ),
                ReferenceCheck.active("so_sales_outbound", "warehouse_id", warehouseId),
                ReferenceCheck.ofActiveParent(
                        "so_sales_outbound_item", "warehouse_id", warehouseId,
                        "so_sales_outbound", "outbound_id"
                ),
                ReferenceCheck.ofActiveParent(
                        "lg_freight_bill_item", "warehouse_id", warehouseId,
                        "lg_freight_bill", "bill_id"
                ),
                ReferenceCheck.ofActiveParent(
                        "st_customer_statement_item", "warehouse_id", warehouseId,
                        "st_customer_statement", "statement_id"
                ),
                ReferenceCheck.ofActiveParent(
                        "st_supplier_statement_item", "warehouse_id", warehouseId,
                        "st_supplier_statement", "statement_id"
                ),
                ReferenceCheck.ofActiveParent(
                        "st_freight_statement_item", "warehouse_id", warehouseId,
                        "st_freight_statement", "statement_id"
                )
        );
    }

    private List<ReferenceCheck> legacyNameReferences(String warehouseName) {
        // 计划类明细允许 warehouse_id 为空；仅这些无稳定标识的历史行回退到名称快照。
        return List.of(
                ReferenceCheck.activeWhen(
                        "po_purchase_inbound", "warehouse_name", warehouseName,
                        "warehouse_id IS NULL"
                ),
                ReferenceCheck.legacyOfActiveParent(
                        "po_purchase_order_item", "warehouse_name", warehouseName, "warehouse_id",
                        "po_purchase_order", "order_id"
                ),
                ReferenceCheck.legacyOfActiveParent(
                        "po_purchase_inbound_item", "warehouse_name", warehouseName, "warehouse_id",
                        "po_purchase_inbound", "inbound_id"
                ),
                ReferenceCheck.legacyOfActiveParent(
                        "so_sales_order_item", "warehouse_name", warehouseName, "warehouse_id",
                        "so_sales_order", "order_id"
                ),
                ReferenceCheck.activeWhen(
                        "so_sales_outbound", "warehouse_name", warehouseName,
                        "warehouse_id IS NULL"
                ),
                ReferenceCheck.legacyOfActiveParent(
                        "so_sales_outbound_item", "warehouse_name", warehouseName, "warehouse_id",
                        "so_sales_outbound", "outbound_id"
                ),
                ReferenceCheck.legacyOfActiveParent(
                        "lg_freight_bill_item", "warehouse_name", warehouseName, "warehouse_id",
                        "lg_freight_bill", "bill_id"
                ),
                ReferenceCheck.legacyOfActiveParent(
                        "st_freight_statement_item", "warehouse_name", warehouseName, "warehouse_id",
                        "st_freight_statement", "statement_id"
                )
        );
    }
}
