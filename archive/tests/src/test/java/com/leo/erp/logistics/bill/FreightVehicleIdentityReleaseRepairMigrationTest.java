package com.leo.erp.logistics.bill;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FreightVehicleIdentityReleaseRepairMigrationTest {

    private static final String MIGRATION =
            "/db/identity-repair-release/D4__repair_freight_vehicle_identity.sql";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    @Test
    void shouldExposeOnlyGenericProtectedManifestPlaceholders() throws IOException {
        String sql = readMigration();
        Matcher matcher = PLACEHOLDER.matcher(sql);
        Set<String> placeholders = matcher.results()
                .map(result -> result.group(1))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(placeholders).containsExactlyInAnyOrder(
                "repairExpectedDatabaseB64",
                "repairD4ManifestB64",
                "repairD4ManifestSha256",
                "repairD4SourceDumpSha256"
        );
        assertThat(sql)
                .contains("convert_from(decode('${repairD4ManifestB64}', 'base64'), 'UTF8')::jsonb")
                .contains("jsonb_to_recordset")
                .contains("pg_catalog.sha256")
                .contains("d4-map-v1")
                .contains("d4-snapshot-v1");
    }

    @Test
    void shouldGateAtV47AndHashTheFrozenSnapshotBeforePersistentWrites() throws IOException {
        String sql = readMigration();
        int fingerprintGate = sql.indexOf("frozen freight snapshot fingerprint mismatch");
        int firstPersistentInsert = sql.indexOf("INSERT INTO public.md_vehicle");
        int firstPersistentUpdate = sql.indexOf("UPDATE public.lg_freight_bill target");

        assertThat(sql)
                .contains("MAX(version::integer)")
                .contains("<> 47")
                .contains("pg_is_in_recovery()")
                .contains("LOCK TABLE public.md_carrier")
                .contains("jsonb_agg(to_jsonb(t) ORDER BY t.id)");
        assertThat(fingerprintGate).isPositive();
        assertThat(firstPersistentInsert).isGreaterThan(fingerprintGate);
        assertThat(firstPersistentUpdate).isGreaterThan(fingerprintGate);
    }

    @Test
    void shouldUpdateBillsByApprovedIdAndRejectRuntimeIdentityGuessing() throws IOException {
        String sql = readMigration();

        assertThat(sql)
                .contains("WHERE target.id = map.bill_id")
                .contains("right(attribute.attname, 3) = '_id'")
                .contains("= ANY ($1)")
                .doesNotContainPattern("(?is)max\\s*\\(\\s*(?:[a-z_][a-z0-9_]*\\.)?(?:id|vehicle_id)\\s*\\)\\s*\\+\\s*1")
                .doesNotContainPattern("(?is)nextval\\s*\\(")
                .doesNotContainPattern("(?is)(?:random|gen_random_uuid|uuid_generate_v4)\\s*\\(")
                .doesNotContain("ON CONFLICT DO NOTHING");
    }

    @Test
    void shouldContainNoEmbeddedProductionSnapshotValues() throws IOException {
        String sql = readMigration();

        assertThat(sql)
                .doesNotContainPattern("[\\p{IsHan}]")
                .doesNotContainPattern("(?<![A-Za-z0-9_])[1-9][0-9]{14,}(?![A-Za-z0-9_])")
                .doesNotContainPattern("(?<![A-Za-z0-9_])[0-9]+\\.[0-9]{2,}(?![A-Za-z0-9_])");
    }

    private String readMigration() throws IOException {
        try (var stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as(MIGRATION).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
