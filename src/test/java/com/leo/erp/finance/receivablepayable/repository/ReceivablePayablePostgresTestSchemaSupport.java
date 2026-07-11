package com.leo.erp.finance.receivablepayable.repository;

import org.springframework.jdbc.core.JdbcTemplate;

final class ReceivablePayablePostgresTestSchemaSupport {

    private ReceivablePayablePostgresTestSchemaSupport() {
    }

    static void preparePurchaseLedgerSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("ALTER TABLE po_purchase_order ADD COLUMN IF NOT EXISTS supplier_code VARCHAR(64)");
        jdbcTemplate.execute("ALTER TABLE po_purchase_inbound ADD COLUMN IF NOT EXISTS supplier_code VARCHAR(64)");
        jdbcTemplate.execute("ALTER TABLE fm_payment ADD COLUMN IF NOT EXISTS payment_purpose VARCHAR(32) NOT NULL DEFAULT 'STATEMENT_SETTLEMENT'");
        jdbcTemplate.execute("ALTER TABLE fm_payment ADD COLUMN IF NOT EXISTS source_purchase_order_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE fm_payment ADD COLUMN IF NOT EXISTS purchase_order_no VARCHAR(64)");
        jdbcTemplate.execute("ALTER TABLE fm_payment ADD COLUMN IF NOT EXISTS supplier_code VARCHAR(64)");
        jdbcTemplate.execute("ALTER TABLE fm_payment ADD COLUMN IF NOT EXISTS supplier_name VARCHAR(128)");
        jdbcTemplate.execute("ALTER TABLE fm_payment ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE fm_payment ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128)");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS fm_supplier_refund_receipt (
                    id BIGINT PRIMARY KEY,
                    version BIGINT NOT NULL DEFAULT 0,
                    refund_receipt_no VARCHAR(64) NOT NULL,
                    purchase_refund_id BIGINT NOT NULL,
                    supplier_code VARCHAR(64) NOT NULL,
                    supplier_name VARCHAR(128) NOT NULL,
                    settlement_company_id BIGINT,
                    settlement_company_name VARCHAR(128),
                    receipt_date DATE NOT NULL,
                    receipt_method VARCHAR(32) NOT NULL,
                    amount NUMERIC(14,2) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    operator_name VARCHAR(32) NOT NULL,
                    remark VARCHAR(255),
                    created_by BIGINT NOT NULL DEFAULT 0,
                    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_by BIGINT,
                    updated_name VARCHAR(64),
                    updated_at TIMESTAMP,
                    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
    }
}
