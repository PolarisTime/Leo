package com.leo.erp.common.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DevelopmentIdentityRepairMigrationTest {

    private static final String SALES_REPAIR =
            "/db/identity-repair-dev/D1__repair_development_sales_identity.sql";
    private static final String SALES_SOURCE_REPAIR =
            "/db/identity-repair-dev/D1_1__repair_development_sales_order_item_source.sql";
    private static final String VEHICLE_REPAIR =
            "/db/identity-repair-dev/D2__repair_development_vehicle_identity.sql";

    @Test
    void shouldGateSalesRepairOnTheLeoDatabaseAtV29() throws IOException {
        String sql = normalizedSql(SALES_REPAIR);

        assertThat(sql)
                .contains("current_database() <> 'leo'")
                .contains("pg_is_in_recovery()")
                .contains("from public.flyway_schema_history")
                .contains("max(version::integer)")
                .contains("<> 29")
                .contains("success = false")
                .contains("lock table public.md_customer")
                .contains("public.md_project")
                .contains("public.so_sales_order")
                .contains("in share row exclusive mode");
    }

    @Test
    void shouldRepairOnlyTheApprovedSalesOrdersWithExplicitIdentity() throws IOException {
        String sql = readMigration(SALES_REPAIR);

        assertThat(sql)
                .contains("${repairHistoricalProjectId}")
                .contains("${repairHistoricalProjectCode}")
                .contains("333565726478565376")
                .contains("333565922142846976")
                .contains("333566047812583424")
                .contains("333572209182244864")
                .contains("333572319848955904")
                .contains("333572387167535104")
                .contains("333668086718660608")
                .contains("900000000000000301")
                .contains("INSERT INTO public.md_project")
                .contains("UPDATE public.so_sales_order target")
                .contains("WHERE target.id = map.order_id")
                .contains("right(attribute.attname, 3) = '_id'")
                .contains("= ANY ($1)");
    }

    @Test
    void shouldIgnoreAsciiEdgeWhitespaceWhenGatingTheHistoricalProjectName() throws IOException {
        String sql = normalizedSql(SALES_REPAIR);

        assertThat(sql).contains(
                "btrim(customer.project_name, e' \\t\\n\\r') "
                        + "<> btrim(approved.project_name, e' \\t\\n\\r')"
        );
    }

    @Test
    void shouldKeepTheInboundSourceAndRemoveOnlyTheApprovedRedundantPurchaseSource() throws IOException {
        String sql = normalizedSql(SALES_SOURCE_REPAIR);

        assertThat(sql)
                .contains("current_database() <> 'leo'")
                .contains("max(version::integer)")
                .contains("<> 32")
                .contains("lock table public.so_sales_order_item")
                .contains("900000000000000302")
                .contains("900000000000000301")
                .contains("900000000000000202")
                .contains("900000000000000102")
                .contains("update public.so_sales_order_item target")
                .contains("set source_purchase_order_item_id = null")
                .contains("target.source_inbound_item_id = approved.source_inbound_item_id")
                .contains("target.source_purchase_order_item_id = approved.source_purchase_order_item_id")
                .doesNotContain("set source_inbound_item_id = null")
                .doesNotContain("on conflict do nothing");
    }

    @Test
    void shouldGateVehicleRepairOnTheLeoDatabaseAtV47() throws IOException {
        String sql = normalizedSql(VEHICLE_REPAIR);

        assertThat(sql)
                .contains("current_database() <> 'leo'")
                .contains("pg_is_in_recovery()")
                .contains("from public.flyway_schema_history")
                .contains("max(version::integer)")
                .contains("<> 47")
                .contains("lock table public.md_carrier")
                .contains("public.md_vehicle")
                .contains("public.lg_freight_bill")
                .contains("in share row exclusive mode");
    }

    @Test
    void shouldRepairOnlyTheApprovedVehicleWithoutRuntimeIdGuessing() throws IOException {
        String sql = readMigration(VEHICLE_REPAIR);

        assertThat(sql)
                .contains("${repairVehicleId}")
                .contains("333737053147627520")
                .contains("浙A12345")
                .contains("INSERT INTO public.md_vehicle")
                .contains("UPDATE public.lg_freight_bill target")
                .contains("WHERE target.id = map.bill_id")
                .doesNotContainPattern("(?is)max\\s*\\(\\s*(?:[a-z_][a-z0-9_]*\\.)?"
                        + "(?:id|project_id|vehicle_id)\\s*\\)\\s*\\+\\s*1")
                .doesNotContainPattern("(?is)nextval\\s*\\(")
                .doesNotContainPattern("(?is)(?:random|gen_random_uuid|uuid_generate_v4)\\s*\\(")
                .doesNotContainPattern("(?is)extract\\s*\\(\\s*epoch\\s+from")
                .doesNotContain("ON CONFLICT DO NOTHING");
    }

    private String normalizedSql(String resource) throws IOException {
        return readMigration(resource)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String readMigration(String resource) throws IOException {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
