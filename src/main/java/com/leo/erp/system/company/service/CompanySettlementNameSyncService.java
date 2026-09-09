package com.leo.erp.system.company.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 结算主体改名时，级联同步各业务表冗余的 settlement_company_name 快照，
 * 避免同一主体出现新旧名称混杂（反规范化快照漂移）。
 */
@Service
public class CompanySettlementNameSyncService {

    /** 冗余了结算主体名称快照的业务表，改名时需级联同步。 */
    private static final String[] SETTLEMENT_COMPANY_NAME_TABLES = {
            "lg_freight_bill",
            "lg_freight_bill_item",
            "md_project",
            "so_sales_order",
            "so_sales_order_item",
            "so_sales_outbound",
            "so_sales_outbound_item",
            "po_purchase_order",
            "po_purchase_inbound",
            "po_purchase_inbound_item",
            "po_purchase_refund",
            "st_customer_statement",
            "st_freight_statement",
            "st_freight_statement_item",
            "st_supplier_statement",
            "fm_receipt",
            "fm_payment",
            "fm_invoice_issue",
            "fm_invoice_receipt",
            "fm_cash_reversal",
            "fm_ledger_adjustment",
            "fm_supplier_refund_receipt",
            "sys_print_template"
    };

    private final JdbcTemplate jdbcTemplate;

    public CompanySettlementNameSyncService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void syncSettlementCompanyName(Long companyId, String companyName) {
        if (jdbcTemplate == null || companyId == null || companyName == null || companyName.isBlank()) {
            return;
        }
        for (String table : SETTLEMENT_COMPANY_NAME_TABLES) {
            jdbcTemplate.update(
                    "UPDATE " + table
                            + " SET settlement_company_name = ? WHERE settlement_company_id = ?",
                    companyName,
                    companyId
            );
        }
    }
}
