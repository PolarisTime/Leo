package com.leo.erp.common.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SalesIdentityReleaseRepairMigrationTest {

    private static final String MIGRATION =
            "/db/identity-repair-release/D3__repair_sales_customer_project_identity.sql";
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
                "repairD3ManifestB64",
                "repairD3ManifestSha256",
                "repairD3SourceDumpSha256"
        );
        assertThat(sql)
                .contains("convert_from(decode('${repairD3ManifestB64}', 'base64'), 'UTF8')::jsonb")
                .contains("jsonb_to_recordset")
                .contains("pg_catalog.sha256")
                .contains("d3-map-v1")
                .contains("d3-snapshot-v1");
    }

    @Test
    void shouldGateAtV29AndHashTheFrozenSnapshotBeforePersistentWrites() throws IOException {
        String sql = readMigration();
        int fingerprintGate = sql.indexOf("frozen sales snapshot fingerprint mismatch");
        int firstPersistentInsert = sql.indexOf("INSERT INTO public.md_project");
        int firstPersistentUpdate = sql.indexOf("UPDATE public.so_sales_order target");

        assertThat(sql)
                .contains("MAX(version::integer)")
                .contains("<> 29")
                .contains("pg_is_in_recovery()")
                .contains("LOCK TABLE public.md_customer")
                .contains("jsonb_agg(to_jsonb(t) ORDER BY t.id)");
        assertThat(fingerprintGate).isPositive();
        assertThat(firstPersistentInsert).isGreaterThan(fingerprintGate);
        assertThat(firstPersistentUpdate).isGreaterThan(fingerprintGate);
    }

    @Test
    void shouldUpdateOrdersByApprovedIdAndRejectRuntimeIdentityGuessing() throws IOException {
        String sql = readMigration();

        assertThat(sql)
                .contains("WHERE target.id = map.order_id")
                .contains("right(attribute.attname, 3) = '_id'")
                .contains("= ANY ($1)")
                .doesNotContainPattern("(?is)max\\s*\\(\\s*(?:[a-z_][a-z0-9_]*\\.)?(?:id|project_id)\\s*\\)\\s*\\+\\s*1")
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
