package com.leo.erp.logistics.bill;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FreightVehicleIdentityRepairMigrationTest {

    private static final String MIGRATION =
            "/db/identity-repair/D2__repair_freight_vehicle_identity.sql";
    private static final String VEHICLE_A0S829_ID = "${repair_vehicle_zhe_a0s829_id}";
    private static final String VEHICLE_A9V967_ID = "${repair_vehicle_zhe_a9v967_id}";

    @Test
    void shouldGateRepairOnAnIntactMainlineAtV47AndLockTheRepairScope() throws IOException {
        String sql = readMigration();

        assertThat(sql)
                .contains("LOCK TABLE public.md_carrier, public.md_vehicle, public.lg_freight_bill")
                .contains("SHARE ROW EXCLUSIVE MODE")
                .contains("public.flyway_schema_history")
                .contains("MAX(version::integer)")
                .contains("<> 47")
                .contains("success = false")
                .contains("RAISE EXCEPTION");
    }

    @Test
    void shouldCheckExactlyFourApprovedBillsAndTwoPlatesInBothDirections() throws IOException {
        String sql = readMigration();

        assertThat(sql)
                .contains("333658934759923712")
                .contains("334254260587864064")
                .contains("334012134176333824")
                .contains("334254367710388224")
                .contains("浙A0S829")
                .contains("浙A9V967");
        assertThat(countOccurrences(sql, "EXCEPT"))
                .as("approved bill snapshot must be compared in both directions")
                .isGreaterThanOrEqualTo(2);
        assertThat(sql)
                .containsPattern("(?is)approved_freight_bill.*EXCEPT.*lg_freight_bill")
                .containsPattern("(?is)lg_freight_bill.*EXCEPT.*approved_freight_bill");
    }

    @Test
    void shouldUseExplicitVehicleIdPlaceholdersAndRejectIdGuessing() throws IOException {
        String sql = readMigration();

        assertThat(sql)
                .contains(VEHICLE_A0S829_ID + "::bigint")
                .contains(VEHICLE_A9V967_ID + "::bigint")
                .contains("INSERT INTO public.md_vehicle")
                .doesNotContain("nextval(")
                .doesNotContain("random(")
                .doesNotContain("clock_timestamp(")
                .doesNotContain("1704038400000")
                .doesNotContain("<< 22")
                .doesNotContain("MIN(id)")
                .doesNotContain("MAX(id)")
                .doesNotContain("ROW_NUMBER(")
                .doesNotContain("ON CONFLICT DO NOTHING");
    }

    private String readMigration() throws IOException {
        try (var stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as(MIGRATION).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ");
        }
    }

    private int countOccurrences(String value, String token) {
        return value.split(Pattern.quote(token), -1).length - 1;
    }
}
