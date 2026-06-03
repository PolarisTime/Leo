package com.leo.erp.common.support;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessEntityRegistrarTest {

    private final BusinessEntityRegistrar registrar = new BusinessEntityRegistrar();

    @Test
    void shouldRegisterAndFindEntityType() {
        registrar.register("test-module", AbstractAuditableEntity.class);

        assertThat(registrar.findEntityType("test-module")).isPresent();
        assertThat(registrar.findEntityType("test-module").get()).isEqualTo(AbstractAuditableEntity.class);
    }

    @Test
    void shouldReturnEmptyForUnknownModule() {
        assertThat(registrar.findEntityType("unknown")).isEmpty();
    }

    @Test
    void shouldCheckIfEntityExists() {
        registrar.register("existing", AbstractAuditableEntity.class);

        assertThat(registrar.hasEntity("existing")).isTrue();
        assertThat(registrar.hasEntity("non-existing")).isFalse();
    }

    @Test
    void shouldReturnModuleKeys() {
        registrar.register("module1", AbstractAuditableEntity.class);
        registrar.register("module2", AbstractAuditableEntity.class);

        assertThat(registrar.moduleKeys()).containsExactly("module1", "module2");
    }

    @Test
    void shouldNormalizeModuleKey() {
        assertThat(BusinessEntityRegistrar.normalizeModuleKey("  /Test-Module  ")).isEqualTo("test-module");
        assertThat(BusinessEntityRegistrar.normalizeModuleKey(null)).isEqualTo("");
        assertThat(BusinessEntityRegistrar.normalizeModuleKey("")).isEqualTo("");
    }

    @Test
    void shouldReturnUnmodifiableSnapshot() {
        registrar.register("snap", AbstractAuditableEntity.class);

        var snapshot = registrar.snapshot();
        assertThat(snapshot).hasSize(1);
        assertThat(snapshot).containsKey("snap");
    }

    @Test
    void shouldOverwriteDuplicateModuleKey() {
        registrar.register("dup", AbstractAuditableEntity.class);
        registrar.register("dup", AbstractAuditableEntity.class);

        assertThat(registrar.moduleKeys()).hasSize(1);
    }
}
