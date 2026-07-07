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
                "purchase-order", "sales-order", "warehouse",
                "permission", "user-account", "role-setting",
                "ledger-adjustment"
        );
    }
}
