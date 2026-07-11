package com.leo.erp.purchase;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseSupplierIdentityMigrationTest {

    private static final String MIGRATION =
            "/db/migration/V14__add_purchase_supplier_identity_snapshots.sql";

    @Test
    void shouldAddAndBackfillStableSupplierCodesWithoutGuessingAmbiguousNames() throws IOException {
        String sql = readMigration().replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("ALTER TABLE public.po_purchase_order ADD COLUMN supplier_code")
                .contains("ALTER TABLE public.po_purchase_inbound ADD COLUMN supplier_code")
                .contains("FROM public.po_purchase_refund refund")
                .contains("COUNT(DISTINCT supplier.supplier_code) = 1")
                .contains("COUNT(DISTINCT source_order.supplier_code) = 1")
                .contains("RAISE EXCEPTION")
                .contains("ALTER COLUMN supplier_code SET NOT NULL");
    }

    @Test
    void shouldIndexActivePurchaseDocumentsByStableSupplierCode() throws IOException {
        String sql = readMigration();

        assertThat(sql)
                .contains("CREATE INDEX idx_po_purchase_order_supplier_code_date")
                .contains("ON public.po_purchase_order (supplier_code, order_date DESC)")
                .contains("CREATE INDEX idx_po_purchase_inbound_supplier_code_date")
                .contains("ON public.po_purchase_inbound (supplier_code, inbound_date DESC)")
                .contains("WHERE deleted_flag = false");
    }

    private String readMigration() throws IOException {
        try (var stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as(MIGRATION).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
