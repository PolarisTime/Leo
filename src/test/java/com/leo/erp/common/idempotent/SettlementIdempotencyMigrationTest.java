package com.leo.erp.common.idempotent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementIdempotencyMigrationTest {

    @Test
    void migrationAddsSettlementUniqueIndexes() throws IOException {
        String sql = new String(
                getClass().getResourceAsStream(
                        "/db/migration/V173__add_settlement_idempotency_constraints.sql"
                ).readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertThat(sql).contains("uk_fm_receipt_allocation_receipt_statement");
        assertThat(sql).contains("ON fm_receipt_allocation (receipt_id, source_statement_id)");
        assertThat(sql).contains("uk_fm_payment_allocation_payment_statement");
        assertThat(sql).contains("ON fm_payment_allocation (payment_id, source_statement_id)");
        assertThat(sql).contains("uk_st_customer_statement_item_source_line");
        assertThat(sql).contains("WHERE source_sales_order_item_id IS NOT NULL");
        assertThat(sql).contains("uk_st_supplier_statement_item_source_line");
        assertThat(sql).contains("WHERE source_inbound_item_id IS NOT NULL");
    }
}
