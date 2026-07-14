package com.leo.erp.common.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SystemIdentityBackfillMigrationsTest {

    @Test
    void shouldProvideFailClosedBackfillForEveryIdentityDomain() throws IOException {
        assertBackfill("/db/migration/V27__backfill_master_identity.sql", "md_project");
        assertBackfill("/db/migration/V28__backfill_inventory_identity.sql", "material_id");
        assertBackfill("/db/migration/V29__backfill_purchase_party_identity.sql", "supplier_id");
        assertBackfill("/db/migration/V30__backfill_sales_party_identity.sql", "customer_id");
        assertBackfill("/db/migration/V31__backfill_logistics_identity.sql", "carrier_id");
        assertBackfill("/db/migration/V32__backfill_finance_typed_identity.sql", "counterparty_id");
        assertBackfill("/db/migration/V33__backfill_source_relationships.sql", "source_");
        assertBackfill("/db/migration/V34__backfill_settlement_identity.sql", "settlement_company_id");
    }

    private void assertBackfill(String resource, String expectedToken) throws IOException {
        String sql = read(resource);
        assertThat(sql)
                .contains("UPDATE ")
                .contains("RAISE EXCEPTION")
                .contains(expectedToken)
                .doesNotContain("SET NOT NULL")
                .doesNotContain("FOREIGN KEY");
    }

    private String read(String resource) throws IOException {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
