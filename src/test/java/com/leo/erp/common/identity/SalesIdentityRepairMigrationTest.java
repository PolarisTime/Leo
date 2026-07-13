package com.leo.erp.common.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SalesIdentityRepairMigrationTest {

    private static final String REPAIR_MIGRATION =
            "/db/identity-repair/D1__repair_sales_customer_project_identity.sql";

    private static final List<String> CUSTOMER_IDS = List.of(
            "333229770902872064",
            "333230231827521536",
            "333658602151616512",
            "333922426578542592"
    );

    private static final List<String> SALES_ORDER_IDS = List.of(
            "333658764227911680",
            "333968132546764800",
            "333969088940351488",
            "334012019617308672",
            "334253683334193152",
            "334253844672290816",
            "334254067603742720"
    );

    @Test
    void shouldRequireTheMainMigrationLineToStopExactlyAtV29() throws IOException {
        String sql = normalizedSql();

        assertThat(sql)
                .contains("from public.flyway_schema_history")
                .containsPattern("(?s)where success\\s*=\\s*true")
                .contains("max(version::integer)")
                .contains("<> 29")
                .contains("raise exception");
    }

    @Test
    void shouldUseExplicitFlywayPlaceholdersForEveryApprovedProjectIdentity() throws IOException {
        String sql = readMigration();

        assertThat(sql)
                .contains("expected_customer_projects")
                .contains("VALUES")
                .contains(CUSTOMER_IDS.toArray(String[]::new));
        for (String customerId : CUSTOMER_IDS) {
            assertThat(sql)
                    .contains("${repairProjectId" + customerId + "}")
                    .contains("${repairProjectCode" + customerId + "}");
        }
    }

    @Test
    void shouldLockRepairTablesAndCompareExpectedAndActualOrdersInBothDirections() throws IOException {
        String sql = normalizedSql();

        assertThat(sql)
                .containsPattern("(?s)lock table\\s+public\\.md_customer\\s*,\\s*"
                        + "public\\.md_project\\s*,\\s*public\\.so_sales_order\\s+"
                        + "in share row exclusive mode")
                .contains("expected_orders")
                .contains("actual_orders")
                .containsPattern("(?s)select \\* from expected_orders\\s+except\\s+"
                        + "select \\* from actual_orders")
                .containsPattern("(?s)select \\* from actual_orders\\s+except\\s+"
                        + "select \\* from expected_orders")
                .contains(SALES_ORDER_IDS.toArray(String[]::new));
    }

    @Test
    void shouldRejectRuntimeProjectIdentityGuessing() throws IOException {
        String sql = readMigration();

        assertThat(sql)
                .doesNotContainPattern("(?is)max\\s*\\(\\s*(?:[a-z_][a-z0-9_]*\\.)?"
                        + "(?:id|project_id)\\s*\\)\\s*\\+\\s*1")
                .doesNotContainPattern("(?is)nextval\\s*\\(")
                .doesNotContainPattern("(?is)(?:random|gen_random_uuid|uuid_generate_v4)\\s*\\(")
                .doesNotContainPattern("(?is)extract\\s*\\(\\s*epoch\\s+from");
    }

    private String normalizedSql() throws IOException {
        return readMigration()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String readMigration() throws IOException {
        try (var input = getClass().getResourceAsStream(REPAIR_MIGRATION)) {
            assertThat(input).as(REPAIR_MIGRATION).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
