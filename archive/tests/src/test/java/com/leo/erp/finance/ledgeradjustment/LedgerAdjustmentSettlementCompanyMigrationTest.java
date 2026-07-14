package com.leo.erp.finance.ledgeradjustment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerAdjustmentSettlementCompanyMigrationTest {

    private static final String MIGRATION =
            "/db/migration/V17__add_ledger_adjustment_settlement_company.sql";
    private static final String DISABLE_MIGRATION =
            "/db/migration/V60__disable_ledger_adjustment_entry.sql";

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

    @Test
    void shouldDocumentHistoricalAdjustmentsAsAuditOnlyAfterEntryIsDisabled() throws IOException {
        String sql = readMigration(DISABLE_MIGRATION).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("COMMENT ON TABLE public.fm_ledger_adjustment")
                .contains("仅供只读审计，不再参与应收应付或供应商净额余额计算")
                .contains("为空的旧记录仅保留在台账调整审计查询")
                .contains("不进入按结算主体查询的财务单据流");
    }

    private String readMigration() throws IOException {
        return readMigration(MIGRATION);
    }

    private String readMigration(String migration) throws IOException {
        try (var stream = getClass().getResourceAsStream(migration)) {
            assertThat(stream).as(migration).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
