package com.leo.erp.system.printtemplate;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PrintTemplateSeedMigrationTest {

    @Test
    void cleanupMigrationOnlyRemovesKnownBaselineTemplateRows() throws IOException {
        String sql = readSql("/db/migration/V3__remove_baseline_print_template_data.sql");

        assertThat(sql).contains("WHERE id IN");
        assertThat(sql).doesNotContain("DELETE FROM sys_print_template;");
        assertThat(sql).doesNotContain("TRUNCATE");
        assertThat(sql).contains(
                "700540000000000001",
                "700540000000000029"
        );
        assertThat(sql).doesNotContain("322775358715723776");
    }

    @Test
    void seedMigrationStoresOnlyFileManagedTemplateMetadata() throws IOException {
        String sql = readSql("/db/seed/S1__seed_print_template_metadata.sql");

        assertThat(sql).contains(
                "print-forms/freight-bill-delivery.layout.json",
                "print-forms/freight-bill-a.lodop.txt",
                "print-forms/freight-statement-summary.lodop.txt",
                "print-forms/customer-statement-a4.lodop.txt",
                "print-forms/yingjie-a4-remark.layout.json",
                "'FILE'"
        );
        assertThat(sql).doesNotContain(
                "物流单 copy（旧版）",
                "A5套打模版",
                "test-template",
                "LODOP.SET_PRINT_PAGESIZE"
        );
    }

    @Test
    void seedMigrationDoesNotOverwriteTemplateCacheOnConflict() throws IOException {
        String sql = readSql("/db/seed/S1__seed_print_template_metadata.sql");

        assertThat(sql).contains("template_html = sys_print_template.template_html");
        assertThat(sql).doesNotContain("template_html = EXCLUDED.template_html");
    }

    @Test
    void defaultPdfSeedMigrationStoresItextLayoutMetadataOnly() throws IOException {
        String sql = readSql("/db/seed/S3__seed_default_pdf_print_template_metadata.sql");

        assertThat(sql).contains(
                "print-forms/default-purchase-order.layout.json",
                "print-forms/default-purchase-inbound.layout.json",
                "print-forms/default-purchase-contract.layout.json",
                "print-forms/default-sales-order.layout.json",
                "print-forms/default-sales-outbound.layout.json",
                "print-forms/default-sales-contract.layout.json",
                "print-forms/default-logistics.layout.json",
                "print-forms/default-customer-statement.layout.json",
                "print-forms/default-supplier-statement.layout.json",
                "print-forms/default-freight-statement.layout.json",
                "'PDF_FORM'",
                "'FILE'",
                "'purchase-order'",
                "'purchase-inbound'",
                "'purchase-contract'",
                "'sales-order'",
                "'sales-outbound'",
                "'sales-contract'",
                "'freight-bill'",
                "'customer-statement'",
                "'supplier-statement'",
                "'freight-statement'"
        );
        assertThat(sql).contains("template_html = sys_print_template.template_html");
        assertThat(sql).doesNotContain(
                "LODOP.",
                "\"form\""
        );
    }

    private String readSql(String resourcePath) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            assertThat(stream).as(resourcePath + " should exist").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
