package com.leo.erp.finance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceDemoDataCleanupMigrationTest {

    @Test
    void baselineDoesNotContainLegacyInvoiceDemoRows() throws IOException {
        String sql = readBaselineSql();

        assertThat(sql).doesNotContain(
                "700780000000000001",
                "700781000000000001",
                "700790000000000001",
                "700791000000000001",
                "2026SP000001",
                "2026KP000001",
                "031002600011",
                "044002600021"
        );
    }

    @Test
    void oldInvoiceDemoCleanupMigrationIsNotOnActiveFlywayPath() {
        assertThat(getClass().getResourceAsStream(
                "/db/migration/V208__remove_invoice_demo_data.sql"
        )).isNull();
    }

    @Test
    void baselineKeepsInvoiceTablesWithoutCleanupDml() throws IOException {
        String sql = readBaselineSql();

        assertThat(sql).contains(
                "CREATE TABLE public.fm_invoice_receipt",
                "CREATE TABLE public.fm_invoice_issue",
                "CREATE TABLE public.fm_invoice_receipt_source_order",
                "CREATE TABLE public.fm_invoice_issue_source_order"
        );
        assertThat(sql).doesNotContain(
                "DELETE FROM fm_invoice_receipt_source_order",
                "DELETE FROM fm_invoice_issue_source_order",
                "DELETE FROM fm_invoice_receipt",
                "DELETE FROM fm_invoice_issue"
        );
    }

    private String readBaselineSql() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
                "/db/migration/V1__baseline.sql"
        )) {
            assertThat(stream).as("V1 baseline should exist").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
