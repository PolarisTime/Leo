package com.leo.erp.common.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SystemIdentityConstraintMigrationsTest {

    @Test
    void shouldExpandFreightStatementPartyIdentityBeforeBackfill() throws IOException {
        String sql = read("/db/migration/V35__expand_freight_statement_party_identity.sql");

        assertThat(sql)
                .contains("ALTER TABLE public.st_freight_statement_item")
                .contains("ADD COLUMN customer_id bigint")
                .contains("ADD COLUMN project_id bigint")
                .doesNotContain("UPDATE ")
                .doesNotContain("FOREIGN KEY")
                .doesNotContain("SET NOT NULL");
    }

    @Test
    void shouldCreateFullSupportingIndexesBeforeForeignKeys() throws IOException {
        String sql = read("/db/migration/V36__add_constraint_supporting_indexes.sql");

        assertThat(sql)
                .contains("idx_st_freight_statement_item_customer_id")
                .contains("idx_st_freight_statement_item_project_id")
                .contains("CREATE UNIQUE INDEX uk_md_project_id_customer_identity")
                .contains("CREATE UNIQUE INDEX uk_lg_freight_bill_item_id_bill_identity")
                .contains("idx_fm_receipt_source_customer_statement_id_fk")
                .contains("idx_fm_payment_source_purchase_order_id_fk")
                .contains("idx_fm_payment_settlement_company_id_fk")
                .doesNotContain("ADD CONSTRAINT")
                .doesNotContain("UPDATE ");
    }

    @Test
    void shouldFailClosedWhenBackfillingFreightStatementPartyIdentity() throws IOException {
        String sql = read("/db/migration/V37__backfill_freight_statement_party_identity.sql");

        assertThat(sql)
                .contains("source_freight_bill_item_id")
                .contains("bill_item.customer_id")
                .contains("bill_item.project_id")
                .contains("UPDATE public.st_freight_statement_item")
                .contains("RAISE EXCEPTION")
                .doesNotContain("FOREIGN KEY")
                .doesNotContain("SET NOT NULL");
    }

    @Test
    void shouldAddForeignKeysAsNotValidBeforeValidation() throws IOException {
        String sql = read("/db/migration/V38__add_stable_identity_foreign_keys.sql");

        assertThat(sql)
                .contains("UNIQUE USING INDEX uk_md_project_id_customer_identity")
                .contains("UNIQUE USING INDEX uk_lg_freight_bill_item_id_bill_identity")
                .contains("FOREIGN KEY (project_id, customer_id)")
                .contains("REFERENCES public.md_project (id, customer_id)")
                .contains("fk_st_freight_statement_item_customer_project")
                .contains("fk_st_freight_statement_item_source_bill_item")
                .contains("fk_fm_payment_allocation_supplier_statement")
                .contains("fk_fm_payment_allocation_freight_statement")
                .contains("fk_fm_receipt_allocation_customer_statement")
                .contains("fk_fm_payment_settlement_company")
                .contains("NOT VALID")
                .doesNotContain("VALIDATE CONSTRAINT")
                .doesNotContain("SET NOT NULL");
    }

    @Test
    void shouldAddTypedSourceAndNotNullChecksAsNotValid() throws IOException {
        String sql = read("/db/migration/V39__add_stable_identity_checks.sql");

        assertThat(sql)
                .contains("num_nonnulls(source_supplier_statement_id, source_freight_statement_id) = 1")
                .contains("source_statement_id = COALESCE(")
                .contains("source_supplier_statement_id,")
                .contains("source_freight_statement_id")
                .contains("source_statement_id = source_customer_statement_id")
                .contains("counterparty_type IN ('供应商', '物流商')")
                .contains("chk_st_freight_statement_item_party_identity_nn")
                .contains("NOT VALID")
                .doesNotContain("VALIDATE CONSTRAINT")
                .doesNotContain("SET NOT NULL");
    }

    @Test
    void shouldValidateForeignKeysInASeparateMigration() throws IOException {
        String sql = read("/db/migration/V40__validate_stable_identity_foreign_keys.sql");

        assertThat(sql)
                .contains("VALIDATE CONSTRAINT fk_md_project_customer_identity")
                .contains("VALIDATE CONSTRAINT fk_st_freight_statement_item_customer_project")
                .contains("VALIDATE CONSTRAINT fk_fm_payment_allocation_supplier_statement")
                .contains("VALIDATE CONSTRAINT fk_fm_payment_settlement_company")
                .doesNotContain("ADD CONSTRAINT")
                .doesNotContain("SET NOT NULL");
    }

    @Test
    void shouldValidateChecksBeforeChangingColumnNullability() throws IOException {
        String sql = read("/db/migration/V41__validate_stable_identity_checks.sql");

        assertThat(sql)
                .contains("VALIDATE CONSTRAINT chk_fm_payment_allocation_typed_source")
                .contains("VALIDATE CONSTRAINT chk_fm_receipt_allocation_typed_source")
                .contains("VALIDATE CONSTRAINT chk_st_freight_statement_item_party_identity_nn")
                .doesNotContain("ADD CONSTRAINT")
                .doesNotContain("SET NOT NULL");
    }

    @Test
    void shouldEnforceNotNullOnlyAfterAllValidationMigrations() throws IOException {
        String sql = read("/db/migration/V42__enforce_stable_identity_not_null.sql");

        assertThat(sql)
                .contains("ALTER COLUMN customer_id SET NOT NULL")
                .contains("ALTER COLUMN project_id SET NOT NULL")
                .contains("ALTER COLUMN material_id SET NOT NULL")
                .contains("ALTER COLUMN supplier_id SET NOT NULL")
                .contains("ALTER COLUMN carrier_id SET NOT NULL")
                .contains("ALTER COLUMN counterparty_id SET NOT NULL")
                .doesNotContain("UPDATE ")
                .doesNotContain("FOREIGN KEY");
    }

    private String read(String resource) throws IOException {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
