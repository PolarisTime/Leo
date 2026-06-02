package com.leo.erp.common.support;

import com.leo.erp.config.BusinessEntityConfig;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessRecordEntityCatalogTest {

    private static final List<String> ATTACHABLE_MODULE_KEYS = List.of(
            "material",
            "supplier",
            "customer",
            "carrier",
            "warehouse",
            "purchase-order",
            "purchase-inbound",
            "sales-order",
            "sales-outbound",
            "freight-bill",
            "purchase-contract",
            "sales-contract",
            "supplier-statement",
            "customer-statement",
            "freight-statement",
            "receipt",
            "payment",
            "invoice-receipt",
            "invoice-issue"
    );

    @BeforeEach
    void setUpCatalog() {
        new BusinessEntityConfig().businessEntityRegistrar();
    }

    @Test
    void shouldExposeOnlyAttachableBusinessModules() {
        assertThat(BusinessRecordEntityCatalog.moduleKeys())
                .containsExactlyElementsOf(ATTACHABLE_MODULE_KEYS)
                .doesNotContain(
                        "settlement-accounts",
                        "inventory-report",
                        "io-report",
                        "pending-invoice-receipt-report",
                        "receivable-payable"
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
        assertThat(BusinessRecordEntityCatalog.findEntityType("/Material")).contains(Material.class);
        assertThat(BusinessRecordEntityCatalog.hasEntity(" MATERIAL ")).isTrue();
    }
}
