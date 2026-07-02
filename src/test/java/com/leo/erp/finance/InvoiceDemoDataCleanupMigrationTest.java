package com.leo.erp.finance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceDemoDataCleanupMigrationTest {

    @Test
    void migrationRemovesLegacyInvoiceDemoRows() throws IOException {
        String sql = readMigrationSql();

        assertThat(sql).contains("DELETE FROM fm_invoice_receipt_item");
        assertThat(sql).contains("DELETE FROM fm_invoice_issue_item");
        assertThat(sql).contains("DELETE FROM fm_invoice_receipt");
        assertThat(sql).contains("DELETE FROM fm_invoice_issue");
        assertThat(sql).contains("700780000000000001");
        assertThat(sql).contains("700781000000000001");
        assertThat(sql).contains("700790000000000001");
        assertThat(sql).contains("700791000000000001");
        assertThat(sql).contains("2026SP000001");
        assertThat(sql).contains("2026KP000001");
        assertThat(sql).contains("031002600011");
        assertThat(sql).contains("044002600021");
    }

    @Test
    void migrationDeletesSourceRelationsBeforeInvoiceHeaders() throws IOException {
        String sql = readMigrationSql();

        assertThat(sql).contains("DELETE FROM fm_invoice_receipt_source_order");
        assertThat(sql).contains("DELETE FROM fm_invoice_issue_source_order");
        assertThat(sql.indexOf("DELETE FROM fm_invoice_receipt_source_order"))
                .isLessThan(sql.indexOf("DELETE FROM fm_invoice_receipt\n"));
        assertThat(sql.indexOf("DELETE FROM fm_invoice_issue_source_order"))
                .isLessThan(sql.indexOf("DELETE FROM fm_invoice_issue\n"));
    }

    private String readMigrationSql() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
                "/db/migration/V208__remove_invoice_demo_data.sql"
        )) {
            assertThat(stream).as("V208 migration should exist").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
