package com.leo.erp.sales.order;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderStatusConstraintMigrationTest {

    private static final String MIGRATION =
            "/db/migration/V56__allow_delivery_verification_sales_order_status.sql";
    private static final String STRICT_FLOW_MIGRATION =
            "/db/migration/V57__strict_sales_logistics_document_flow.sql";
    private static final String LEGACY_STATUS_MIGRATION =
            "/db/migration/V61__normalize_legacy_sales_order_status.sql";

    @Test
    void shouldExpandSalesOrderStatusWithoutChangingExistingRows() throws IOException {
        String sql = readMigration(MIGRATION);

        assertThat(sql)
                .contains("ADD CONSTRAINT chk_so_status_v56")
                .contains("'草稿', '已审核', '待完善', '交付核定', '完成销售'")
                .contains("NOT VALID")
                .contains("VALIDATE CONSTRAINT chk_so_status_v56")
                .contains("DROP CONSTRAINT chk_so_status")
                .contains("RENAME CONSTRAINT chk_so_status_v56 TO chk_so_status")
                .doesNotContain("UPDATE public.so_sales_order")
                .doesNotContain("DELETE FROM public.so_sales_order");

        assertThat(sql.indexOf("VALIDATE CONSTRAINT chk_so_status_v56"))
                .isLessThan(sql.indexOf("DROP CONSTRAINT chk_so_status"));
    }

    @Test
    void shouldIntroduceStableSalesOrderFreightOutboundLinks() throws IOException {
        String sql = readMigration(STRICT_FLOW_MIGRATION);

        assertThat(sql)
                .contains("source_sales_order_id")
                .contains("source_sales_order_item_id")
                .contains("source_freight_bill_id")
                .contains("status = '草稿'")
                .contains("uk_lg_freight_bill_active_sales_order")
                .contains("uk_so_sales_outbound_active_freight_bill");
    }

    @Test
    void shouldNormalizeLegacyStatusBeforeTighteningConstraint() throws IOException {
        String sql = readMigration(LEGACY_STATUS_MIGRATION);

        assertThat(sql)
                .contains("SET status = '交付核定'")
                .contains("WHERE status = '待完善'")
                .contains("CHECK (status IN ('草稿', '已审核', '交付核定', '完成销售'))");
    }

    private String readMigration(String migration) throws IOException {
        try (var stream = getClass().getResourceAsStream(migration)) {
            assertThat(stream).as(migration).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
