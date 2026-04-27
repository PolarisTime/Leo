package com.leo.erp.common.support;

import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessRecordEntityCatalogTest {

    private static final List<String> ATTACHABLE_MODULE_KEYS = List.of(
            "materials",
            "suppliers",
            "customers",
            "carriers",
            "warehouses",
            "purchase-orders",
            "purchase-inbounds",
            "sales-orders",
            "sales-outbounds",
            "freight-bills",
            "purchase-contracts",
            "sales-contracts",
            "supplier-statements",
            "customer-statements",
            "freight-statements",
            "receipts",
            "payments",
            "invoice-receipts",
            "invoice-issues"
    );

    @Test
    void shouldExposeOnlyAttachableBusinessModules() {
        assertThat(BusinessRecordEntityCatalog.moduleKeys())
                .containsExactlyElementsOf(ATTACHABLE_MODULE_KEYS)
                .doesNotContain(
                        "settlement-accounts",
                        "inventory-report",
                        "io-report",
                        "pending-invoice-receipt-report",
                        "receivables-payables"
                );
    }

    @Test
    void shouldResolveAttachableModulesToBusinessResources() {
        ModuleCatalog moduleCatalog = new ModuleCatalog();

        assertThat(BusinessRecordEntityCatalog.moduleKeys()).allSatisfy(moduleKey -> {
            assertThat(moduleCatalog.containsModule(moduleKey)).as(moduleKey).isTrue();
            String resource = ResourcePermissionCatalog.resolveResourceByMenuCode(moduleKey).orElse(null);
            assertThat(resource).as(moduleKey).isNotNull();
            assertThat(ResourcePermissionCatalog.isBusinessResource(resource)).as(moduleKey).isTrue();
        });
    }

    @Test
    void shouldNormalizeModuleKeyBeforeLookup() {
        assertThat(BusinessRecordEntityCatalog.findEntityType("/Materials")).contains(Material.class);
        assertThat(BusinessRecordEntityCatalog.hasEntity(" MATERIALS ")).isTrue();
    }
}
