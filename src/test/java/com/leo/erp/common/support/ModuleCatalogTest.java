package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleCatalogTest {

    private final ModuleCatalog catalog = new ModuleCatalog();

    @Test
    void shouldReturnOrderedModuleKeys() {
        var keys = catalog.orderedModuleKeys();
        assertThat(keys).isNotEmpty();
        assertThat(keys).contains("material", "supplier", "customer");
    }

    @Test
    void shouldResolveModuleName() {
        assertThat(catalog.resolveModuleName("material")).isEqualTo("商品资料");
        assertThat(catalog.resolveModuleName("supplier")).isEqualTo("供应商");
        assertThat(catalog.resolveModuleName("customer")).isEqualTo("客户");
        assertThat(catalog.resolveModuleName("purchase-refund")).isEqualTo("采购退款单");
        assertThat(catalog.resolveModuleName("supplier-refund-receipt")).isEqualTo("供应商退款到账单");
    }

    @Test
    void shouldReturnKeyWhenModuleUnknown() {
        assertThat(catalog.resolveModuleName("unknown-module")).isEqualTo("unknown-module");
    }

    @Test
    void shouldContainKnownModule() {
        assertThat(catalog.containsModule("material")).isTrue();
        assertThat(catalog.containsModule("material-categories")).isTrue();
        assertThat(catalog.containsModule("purchase-order")).isTrue();
        assertThat(catalog.containsModule("purchase-refund")).isTrue();
        assertThat(catalog.containsModule("supplier-refund-receipt")).isTrue();
        assertThat(catalog.containsModule("ledger-adjustment")).isTrue();
    }

    @Test
    void shouldResolveAliasModuleName() {
        assertThat(catalog.resolveModuleName("material-categories")).isEqualTo("商品类别");
    }

    @Test
    void shouldNotContainUnknownModule() {
        assertThat(catalog.containsModule("non-existing")).isFalse();
    }

    @Test
    void shouldNotContainNullModule() {
        assertThat(catalog.containsModule(null)).isFalse();
    }

    @Test
    void shouldContainAllExpectedModules() {
        var keys = catalog.orderedModuleKeys();
        assertThat(keys).contains(
                "material", "supplier", "customer", "project",
                "purchase-order", "purchase-refund", "supplier-refund-receipt", "sales-order", "warehouse",
                "permission", "user-account", "role-setting",
                "ledger-adjustment"
        );
    }
}
