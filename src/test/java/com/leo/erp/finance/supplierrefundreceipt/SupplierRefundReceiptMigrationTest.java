package com.leo.erp.finance.supplierrefundreceipt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierRefundReceiptMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V16__add_purchase_prepayment_and_supplier_refund_receipt.sql"
    );

    @Test
    void shouldAddPurchasePrepaymentIdentityAndPurposeConstraints() throws IOException {
        String sql = normalizedSql();

        assertThat(sql)
                .contains("alter table public.fm_payment")
                .contains("payment_purpose character varying(32) not null default 'statement_settlement'")
                .contains("source_purchase_order_id bigint")
                .contains("purchase_order_no character varying(64)")
                .contains("supplier_code character varying(64)")
                .contains("supplier_name character varying(128)")
                .contains("settlement_company_id bigint")
                .contains("settlement_company_name character varying(128)")
                .contains("payment_purpose in ('statement_settlement', 'purchase_prepayment')")
                .contains("payment_purpose <> 'purchase_prepayment'")
                .contains("source_statement_id is null")
                .contains("payment_purpose <> 'statement_settlement'")
                .contains("foreign key (source_purchase_order_id)")
                .contains("references public.po_purchase_order(id)");
    }

    @Test
    void shouldCreateInstallmentSupplierRefundReceiptWithDatabaseInvariants() throws IOException {
        String sql = normalizedSql();

        assertThat(sql)
                .contains("create table public.fm_supplier_refund_receipt")
                .contains("refund_receipt_no character varying(64) not null")
                .contains("purchase_refund_id bigint not null")
                .contains("supplier_code character varying(64) not null")
                .contains("supplier_name character varying(128) not null")
                .contains("receipt_date date not null")
                .contains("receipt_method character varying(32) not null")
                .contains("amount numeric(14,2) not null")
                .contains("amount > 0")
                .contains("status in ('草稿', '已收款')")
                .contains("foreign key (purchase_refund_id)")
                .contains("references public.po_purchase_refund(id)")
                .contains("idx_fm_supplier_refund_receipt_refund_status")
                .doesNotContain("source_purchase_order_id bigint not null");
    }

    @Test
    void shouldSeedModulePermissionAndNumberRule() throws IOException {
        String sql = normalizedSql();

        assertThat(sql)
                .contains("'supplier-refund-receipt'")
                .contains("'供应商退款到账单'")
                .contains("'rule_srr'")
                .contains("'srr{yyyy}{seq}'")
                .contains("public.sys_menu")
                .contains("public.sys_menu_action")
                .contains("public.sys_role_permission")
                .contains("public.sys_no_rule");
    }

    private String normalizedSql() throws IOException {
        return Files.readString(MIGRATION)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }
}
