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
                "print-forms/default-logistics.layout.json",
                "print-forms/default-purchase.layout.json",
                "print-forms/default-report.layout.json",
                "print-forms/default-sales.layout.json",
                "print-forms/default-statement.layout.json",
                "'PDF_FORM'",
                "'FILE'",
                "e4586f52f923ef0151446a3ca9bfbdac3ddc987fdce87425992fc62702d35b65",
                "9960719640e2a0fc20ae485365ae000f8010b9c92509f906db0dc38728ed05c1",
                "a644b9ad4a1126d3a44f5e731d77fb1924349b1c64f301ec6d69ebda136f217f",
                "e327d7b6fab2ca0b445e945c798f93ea04cb8e1d7c6faca668d35722349f0962",
                "7beb49929a784b611dcd436f248c2a8e8eca3085dd5ff0bfefa16088a439648e"
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
