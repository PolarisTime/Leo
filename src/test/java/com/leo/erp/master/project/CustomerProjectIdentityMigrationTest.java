package com.leo.erp.master.project;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerProjectIdentityMigrationTest {

    private static final String MIGRATION =
            "/db/migration/V20__expand_customer_project_identity.sql";

    @Test
    void shouldOnlyExpandCustomerProjectIdentityColumns() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("ALTER TABLE public.md_project")
                .contains("ALTER TABLE public.so_sales_order")
                .contains("ADD COLUMN customer_id bigint")
                .doesNotContain("UPDATE ")
                .doesNotContain("SET NOT NULL")
                .doesNotContain("FOREIGN KEY");
    }
}
