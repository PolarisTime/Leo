package com.leo.erp.common.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SystemIdentityExpandMigrationsTest {

    @Test
    void shouldExpandInventoryIdentityWithoutBackfillOrConstraints() throws IOException {
        String sql = read("/db/migration/V21__expand_inventory_identity.sql");

        assertExpandOnly(sql);
        assertThat(sql)
                .contains("po_purchase_order_item")
                .contains("material_id bigint")
                .contains("warehouse_id bigint")
                .contains("batch_no_normalized character varying(64)")
                .contains("GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED");
    }

    @Test
    void shouldExpandPurchasePartyIdentityWithoutBackfillOrConstraints() throws IOException {
        String sql = read("/db/migration/V22__expand_purchase_party_identity.sql");

        assertExpandOnly(sql);
        assertThat(sql)
                .contains("ct_purchase_contract")
                .contains("po_purchase_order")
                .contains("po_purchase_inbound")
                .contains("po_purchase_refund")
                .contains("fm_invoice_receipt")
                .contains("st_supplier_statement")
                .contains("fm_supplier_refund_receipt")
                .contains("supplier_id bigint");
    }

    @Test
    void shouldExpandRemainingSalesPartyIdentityWithoutBackfillOrConstraints() throws IOException {
        String sql = read("/db/migration/V23__expand_remaining_sales_party_identity.sql");

        assertExpandOnly(sql);
        assertThat(sql)
                .contains("ct_sales_contract")
                .contains("so_sales_outbound")
                .contains("fm_invoice_issue")
                .contains("st_customer_statement")
                .contains("fm_receipt")
                .contains("lg_freight_bill_item")
                .contains("customer_id bigint")
                .contains("project_id bigint");
    }

    @Test
    void shouldExpandLogisticsIdentityWithoutBackfillOrConstraints() throws IOException {
        String sql = read("/db/migration/V24__expand_logistics_identity.sql");

        assertExpandOnly(sql);
        assertThat(sql)
                .contains("lg_freight_bill")
                .contains("st_freight_statement")
                .contains("carrier_id bigint")
                .contains("source_freight_bill_id bigint")
                .contains("source_freight_bill_item_id bigint");
    }

    @Test
    void shouldExpandTypedFinanceIdentityWithoutBackfillOrConstraints() throws IOException {
        String sql = read("/db/migration/V25__expand_finance_typed_identity.sql");

        assertExpandOnly(sql);
        assertThat(sql)
                .contains("fm_payment")
                .contains("counterparty_type character varying(32)")
                .contains("counterparty_id bigint")
                .contains("source_supplier_statement_id bigint")
                .contains("source_freight_statement_id bigint")
                .contains("source_customer_statement_id bigint")
                .contains("fm_ledger_adjustment");
    }

    @Test
    void shouldAddOnlySupportingIndexesInV26() throws IOException {
        String sql = read("/db/migration/V26__add_identity_supporting_indexes.sql");

        assertThat(sql)
                .contains("CREATE INDEX")
                .contains("material_id")
                .contains("warehouse_id")
                .contains("customer_id")
                .contains("supplier_id")
                .contains("carrier_id")
                .contains("counterparty_id")
                .doesNotContain("UPDATE ")
                .doesNotContain("FOREIGN KEY")
                .doesNotContain("SET NOT NULL");
    }

    private void assertExpandOnly(String sql) {
        assertThat(sql)
                .contains("ADD COLUMN")
                .doesNotContain("UPDATE ")
                .doesNotContain("FOREIGN KEY")
                .doesNotContain("SET NOT NULL");
    }

    private String read(String resource) throws IOException {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
