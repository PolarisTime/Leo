package com.leo.erp.common.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SystemIdentityFinalContractMigrationsTest {

    @Test
    void shouldAddVehicleOwnershipConstraintBeforeValidation() throws IOException {
        String sql = read("/db/migration/V48__add_vehicle_carrier_ownership_constraints.sql");

        assertThat(sql)
                .contains("CREATE UNIQUE INDEX uk_md_vehicle_id_carrier_identity")
                .contains("ON public.md_vehicle (id, carrier_id)")
                .contains("CREATE INDEX idx_lg_freight_bill_vehicle_carrier_identity")
                .contains("ON public.lg_freight_bill (vehicle_id, carrier_id)")
                .contains("FOREIGN KEY (vehicle_id, carrier_id)")
                .contains("REFERENCES public.md_vehicle (id, carrier_id)")
                .contains("chk_lg_freight_bill_vehicle_snapshot_pair")
                .contains("vehicle_id IS NULL")
                .contains("NULLIF(BTRIM(vehicle_plate), '') IS NULL")
                .contains("NOT VALID")
                .doesNotContain("VALIDATE CONSTRAINT");
        assertThat(countOccurrences(sql, "CREATE ")).isEqualTo(2);
        assertThat(countOccurrences(sql, "ADD CONSTRAINT")).isEqualTo(2);
    }

    @Test
    void shouldValidateVehicleOwnershipInSeparateMigration() throws IOException {
        String sql = read("/db/migration/V49__validate_vehicle_carrier_ownership_constraints.sql");

        assertThat(sql)
                .contains("VALIDATE CONSTRAINT fk_lg_freight_bill_vehicle_carrier_identity")
                .contains("VALIDATE CONSTRAINT chk_lg_freight_bill_vehicle_snapshot_pair")
                .doesNotContain("ADD CONSTRAINT")
                .doesNotContain("CREATE INDEX");
    }

    @Test
    void shouldAddOnlyMissingFullIndexesForSystemRelations() throws IOException {
        String sql = read("/db/migration/V50__add_system_relation_fk_indexes.sql");

        assertThat(sql)
                .contains("CREATE INDEX idx_sys_role_parent_id_fk")
                .contains("ON public.sys_role (parent_id)")
                .contains("CREATE INDEX idx_sys_attachment_binding_attachment_id_fk")
                .contains("ON public.sys_attachment_binding (attachment_id)")
                .doesNotContain("WHERE")
                .doesNotContain("ADD CONSTRAINT");
        assertThat(countOccurrences(sql, "CREATE INDEX")).isEqualTo(2);
    }

    @Test
    void shouldAddAndValidateOrdinarySystemRelationsSeparately() throws IOException {
        String add = read("/db/migration/V51__add_system_relation_foreign_keys.sql");
        String validate = read("/db/migration/V52__validate_system_relation_foreign_keys.sql");

        assertThat(add)
                .contains("fk_auth_api_key_user")
                .contains("fk_auth_refresh_token_user")
                .contains("fk_sys_user_role_user")
                .contains("fk_sys_user_role_role")
                .contains("fk_sys_role_permission_role")
                .contains("fk_sys_role_parent")
                .contains("fk_sys_department_parent")
                .contains("fk_sys_attachment_binding_attachment")
                .contains("NOT VALID")
                .doesNotContain("VALIDATE CONSTRAINT")
                .doesNotContain("record_id");
        assertThat(countOccurrences(add, "FOREIGN KEY")).isEqualTo(8);
        assertThat(validate)
                .contains("VALIDATE CONSTRAINT fk_auth_api_key_user")
                .contains("VALIDATE CONSTRAINT fk_auth_refresh_token_user")
                .contains("VALIDATE CONSTRAINT fk_sys_user_role_user")
                .contains("VALIDATE CONSTRAINT fk_sys_user_role_role")
                .contains("VALIDATE CONSTRAINT fk_sys_role_permission_role")
                .contains("VALIDATE CONSTRAINT fk_sys_role_parent")
                .contains("VALIDATE CONSTRAINT fk_sys_department_parent")
                .contains("VALIDATE CONSTRAINT fk_sys_attachment_binding_attachment")
                .doesNotContain("ADD CONSTRAINT");
        assertThat(countOccurrences(validate, "VALIDATE CONSTRAINT")).isEqualTo(8);
    }

    @Test
    void shouldEnforcePurchaseInboundDirectSourceInThreePhases() throws IOException {
        String add = read("/db/migration/V53__add_purchase_inbound_direct_source_check.sql");
        String validate = read("/db/migration/V54__validate_purchase_inbound_direct_source_check.sql");
        String enforce = read("/db/migration/V55__enforce_purchase_inbound_direct_source_not_null.sql");

        assertThat(add)
                .contains("chk_po_purchase_inbound_item_source_identity_nn")
                .contains("CHECK (source_purchase_order_item_id IS NOT NULL) NOT VALID")
                .doesNotContain("VALIDATE CONSTRAINT")
                .doesNotContain("SET NOT NULL");
        assertThat(validate)
                .contains("VALIDATE CONSTRAINT chk_po_purchase_inbound_item_source_identity_nn")
                .doesNotContain("ADD CONSTRAINT")
                .doesNotContain("SET NOT NULL");
        assertThat(enforce)
                .contains("ALTER COLUMN source_purchase_order_item_id SET NOT NULL")
                .contains("DROP CONSTRAINT chk_po_purchase_inbound_item_source_identity_nn")
                .doesNotContain("UPDATE ")
                .doesNotContain("FOREIGN KEY");
    }

    private String read(String resource) throws IOException {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int countOccurrences(String value, String token) {
        String sql = value.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--[^\\r\\n]*", "");
        return sql.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }
}
