package com.leo.erp.sales.order;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderStatusConstraintMigrationTest {

    private static final String MIGRATION =
            "/db/migration/V56__allow_delivery_verification_sales_order_status.sql";

    @Test
    void shouldExpandSalesOrderStatusWithoutChangingExistingRows() throws IOException {
        String sql = readMigration();

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

    private String readMigration() throws IOException {
        try (var stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as(MIGRATION).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
