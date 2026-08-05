package com.leo.erp.master.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 主数据改名时级联同步各业务表冗余的名称快照（反规范化快照漂移修复）。
 * <p>
 * 业务表冗余存储主数据名称（customer_name/supplier_name/project_name/warehouse_name/
 * carrier_name/material_name），主数据改名后若不级联更新，会出现同一主体新旧名称混杂。
 * 本服务集中管理各主数据引用表清单，表名/列名均为硬编码白名单（防注入），值用占位符参数。
 */
@Service
public class ReferenceSnapshotSyncService {

    private final JdbcTemplate jdbcTemplate;

    public ReferenceSnapshotSyncService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void syncCustomerName(Long id, String name) {
        updateSnapshot("customer_id", "customer_name", id, name,
                "ct_sales_contract", "fm_invoice_issue", "fm_receipt", "lg_freight_bill_item",
                "so_sales_order", "so_sales_outbound", "st_customer_statement", "st_freight_statement_item");
    }

    public void syncSupplierName(Long id, String name) {
        updateSnapshot("supplier_id", "supplier_name", id, name,
                "ct_purchase_contract", "fm_invoice_receipt", "fm_supplier_refund_receipt", "po_purchase_inbound",
                "po_purchase_order", "po_purchase_refund", "st_supplier_statement");
    }

    public void syncProjectName(Long id, String name) {
        updateSnapshot("project_id", "project_name", id, name,
                "ct_sales_contract", "fm_invoice_issue", "fm_ledger_adjustment", "fm_receipt", "lg_freight_bill_item",
                "so_sales_order", "so_sales_outbound", "st_customer_statement", "st_freight_statement_item");
    }

    public void syncWarehouseName(Long id, String name) {
        updateSnapshot("warehouse_id", "warehouse_name", id, name,
                "fm_invoice_issue_item", "fm_invoice_receipt_item", "lg_freight_bill_item", "po_purchase_inbound",
                "po_purchase_inbound_item", "po_purchase_order_item", "po_purchase_refund_item", "so_sales_order_item",
                "so_sales_outbound", "so_sales_outbound_item", "st_freight_statement_item");
    }

    public void syncCarrierName(Long id, String name) {
        updateSnapshot("carrier_id", "carrier_name", id, name,
                "lg_freight_bill", "st_freight_statement");
    }

    public void syncMaterialName(Long id, String name) {
        updateSnapshot("material_id", "material_name", id, name,
                "lg_freight_bill_item", "st_freight_statement_item");
    }

    private void updateSnapshot(String idColumn, String nameColumn, Long id, String name, String... tables) {
        if (jdbcTemplate == null || id == null || name == null || name.isBlank()) {
            return;
        }
        for (String table : tables) {
            jdbcTemplate.update(
                    "UPDATE " + table + " SET " + nameColumn + " = ? WHERE " + idColumn + " = ?",
                    name,
                    id
            );
        }
    }
}
