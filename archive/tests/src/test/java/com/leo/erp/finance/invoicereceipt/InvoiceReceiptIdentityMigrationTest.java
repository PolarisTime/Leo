package com.leo.erp.finance.invoicereceipt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceReceiptIdentityMigrationTest {

    private static final String MIGRATION =
            "/db/migration/V15__add_invoice_receipt_identity_snapshots.sql";

    @Test
    void shouldBackfillSupplierAndSettlementIdentityFromPurchaseSources() throws IOException {
        String sql = readMigration().replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("ALTER TABLE public.fm_invoice_receipt ADD COLUMN supplier_code")
                .contains("ADD COLUMN settlement_company_id")
                .contains("ADD COLUMN settlement_company_name")
                .contains("JOIN public.po_purchase_order_item source_item")
                .contains("JOIN public.po_purchase_order source_order")
                .contains("COUNT(DISTINCT source_order.supplier_code) = 1")
                .contains("ALTER COLUMN supplier_code SET NOT NULL")
                .contains("invoice_title = settlement_company_name");
    }

    @Test
    void shouldIndexInvoiceReceiptsByStableSupplierAndSettlementCompany() throws IOException {
        String sql = readMigration();

        assertThat(sql)
                .contains("CREATE INDEX idx_fm_invoice_receipt_supplier_code_date")
                .contains("CREATE INDEX idx_fm_invoice_receipt_settlement_company_date")
                .contains("WHERE deleted_flag = false");
    }

    private String readMigration() throws IOException {
        try (var stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as(MIGRATION).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
