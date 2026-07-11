package com.leo.erp.logistics.bill;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FreightBillCarrierIdentityMigrationTest {

    private static final String MIGRATION =
            "/db/migration/V18__add_freight_bill_carrier_code.sql";

    @Test
    void shouldBackfillOnlyUnambiguousActiveCarrierCodesAndFailClosed() throws IOException {
        String sql = readMigration().replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("ALTER TABLE public.lg_freight_bill ADD COLUMN carrier_code")
                .contains("carrier.deleted_flag = false")
                .contains("COUNT(DISTINCT carrier.carrier_code) = 1")
                .contains("RAISE EXCEPTION")
                .contains("ALTER COLUMN carrier_code SET NOT NULL")
                .contains("chk_lg_freight_bill_carrier_code_not_blank")
                .contains("chk_st_freight_statement_carrier_code_not_blank");
    }

    @Test
    void shouldBackfillStatementsFromSourceBillsWithoutOverwritingConflicts() throws IOException {
        String sql = readMigration().replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("JOIN public.lg_freight_bill source_bill")
                .contains("COUNT(DISTINCT source_bill.carrier_code) = 1")
                .contains("物流对账单物流商编码与来源物流单冲突")
                .contains("UPDATE public.st_freight_statement freight_statement")
                .contains("ALTER COLUMN carrier_code SET NOT NULL");
    }

    @Test
    void shouldIndexAndDocumentStableCarrierIdentity() throws IOException {
        String sql = readMigration();

        assertThat(sql)
                .contains("COMMENT ON COLUMN public.lg_freight_bill.carrier_code")
                .contains("CREATE INDEX idx_lg_freight_bill_carrier_code_date")
                .contains("ON public.lg_freight_bill (carrier_code, bill_time DESC)")
                .contains("WHERE deleted_flag = false");
    }

    private String readMigration() throws IOException {
        try (var stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as(MIGRATION).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
