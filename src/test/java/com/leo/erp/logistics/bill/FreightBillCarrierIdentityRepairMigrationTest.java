package com.leo.erp.logistics.bill;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FreightBillCarrierIdentityRepairMigrationTest {

    private static final String MIGRATION =
            "/db/migration/V19__repair_freight_carrier_code_constraints.sql";

    @Test
    void shouldAddMissingCarrierCodeChecksIdempotently() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("chk_lg_freight_bill_carrier_code_not_blank")
                .contains("chk_st_freight_statement_carrier_code_not_blank")
                .contains("NOT EXISTS")
                .contains("BTRIM(carrier_code) <> ''");
    }
}
