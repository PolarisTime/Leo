package com.leo.erp.finance.ledgeradjustment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerAdjustmentSettlementCompanyMigrationTest {

    private static final String MIGRATION =
            "/db/migration/V17__add_ledger_adjustment_settlement_company.sql";

    @Test
    void shouldAddLedgerAdjustmentSettlementCompanySnapshotAndIntegrityConstraint() throws IOException {
        String sql = readMigration().replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("ALTER TABLE public.fm_ledger_adjustment")
                .contains("ADD COLUMN settlement_company_id bigint")
                .contains("ADD COLUMN settlement_company_name character varying(128)")
                .contains("chk_fm_ledger_adjustment_settlement_company_pair")
                .contains("settlement_company_id IS NULL")
                .contains("settlement_company_id IS NOT NULL")
                .contains("NULLIF(BTRIM(settlement_company_name), '') IS NOT NULL");
    }

    @Test
    void shouldIndexActiveAdjustmentsBySettlementCompanyAndDate() throws IOException {
        String sql = readMigration();

        assertThat(sql)
                .contains("CREATE INDEX idx_fm_ledger_adjustment_settlement_company_date")
                .contains("ON public.fm_ledger_adjustment (settlement_company_id, adjustment_date DESC)")
                .contains("WHERE deleted_flag = false");
    }

    private String readMigration() throws IOException {
        try (var stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as(MIGRATION).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
