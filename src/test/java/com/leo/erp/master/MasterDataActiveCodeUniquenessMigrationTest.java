package com.leo.erp.master;

import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MasterDataActiveCodeUniquenessMigrationTest {

    private static final Map<Class<?>, String> MASTER_CODE_FIELDS = Map.of(
            Carrier.class, "carrierCode",
            Customer.class, "customerCode",
            Material.class, "materialCode",
            Project.class, "projectCode",
            Supplier.class, "supplierCode",
            Warehouse.class, "warehouseCode"
    );

    @Test
    void masterDataEntitiesShouldNotDeclarePermanentCodeUniqueness() throws Exception {
        for (var entry : MASTER_CODE_FIELDS.entrySet()) {
            Column column = entry.getKey().getDeclaredField(entry.getValue()).getAnnotation(Column.class);

            assertThat(column.unique())
                    .as("%s.%s", entry.getKey().getSimpleName(), entry.getValue())
                    .isFalse();
        }
    }

    @Test
    void v11ShouldReplacePermanentMasterCodeConstraintsWithActiveIndexes() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V11__use_active_master_data_code_uniqueness.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertActiveUniqueMigration(sql, "md_carrier", "carrier_code");
            assertActiveUniqueMigration(sql, "md_customer", "customer_code");
            assertActiveUniqueMigration(sql, "md_material", "material_code");
            assertActiveUniqueMigration(sql, "md_project", "project_code");
            assertActiveUniqueMigration(sql, "md_supplier", "supplier_code");
            assertActiveUniqueMigration(sql, "md_warehouse", "warehouse_code");
        }
    }

    private void assertActiveUniqueMigration(String sql, String table, String column) {
        assertThat(sql)
                .contains("ALTER TABLE public.%s DROP CONSTRAINT %s_%s_key".formatted(table, table, column))
                .containsPattern((
                        "(?s)CREATE UNIQUE INDEX uk_%s_%s_active\\s+"
                                + "ON public\\.%s \\(%s\\)\\s+"
                                + "WHERE deleted_flag = false;"
                ).formatted(table, column, table, column));
    }
}
