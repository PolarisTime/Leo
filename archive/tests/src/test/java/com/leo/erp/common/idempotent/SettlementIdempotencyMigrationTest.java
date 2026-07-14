package com.leo.erp.common.idempotent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementIdempotencyMigrationTest {

    @Test
    void baselineKeepsSettlementUniqueIndexes() throws IOException {
        String sql = readBaselineSql();

        assertThat(sql).contains("uk_fm_receipt_allocation_receipt_statement");
        assertThat(sql).contains("ON public.fm_receipt_allocation USING btree (receipt_id, source_statement_id)");
        assertThat(sql).contains("uk_fm_payment_allocation_payment_statement");
        assertThat(sql).contains("ON public.fm_payment_allocation USING btree (payment_id, source_statement_id)");
        assertThat(sql).contains("uk_st_customer_statement_item_source_line");
        assertThat(sql).contains("WHERE (source_sales_order_item_id IS NOT NULL)");
        assertThat(sql).contains("uk_st_supplier_statement_item_source_line");
        assertThat(sql).contains("WHERE (source_inbound_item_id IS NOT NULL)");
    }

    @Test
    void oldSettlementMigrationIsNotOnActiveFlywayPath() throws IOException {
        String sql = readBaselineSql();

        assertThat(getClass().getResourceAsStream(
                "/db/migration/V173__add_settlement_idempotency_constraints.sql"
        )).isNull();
        assertThat(sql).doesNotContain("Duplicate fm_receipt_allocation receipt_id/source_statement_id rows");
    }

    private String readBaselineSql() throws IOException {
        return new String(
                getClass().getResourceAsStream("/db/migration/V1__baseline.sql").readAllBytes(),
                StandardCharsets.UTF_8
        );
    }
}
