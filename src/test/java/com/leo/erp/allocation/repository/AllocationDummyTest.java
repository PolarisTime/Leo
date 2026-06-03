package com.leo.erp.allocation.repository;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationDummyTest {

    @Test
    void shouldCreateInstanceWithDefaultConstructor() {
        AllocationDummy dummy = new AllocationDummy();

        assertThat(dummy).isNotNull();
    }

    @Test
    void shouldSetAndGetId() throws Exception {
        AllocationDummy dummy = new AllocationDummy();
        Field idField = AllocationDummy.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(dummy, 100L);

        assertThat(idField.get(dummy)).isEqualTo(100L);
    }

    @Test
    void shouldHandleNullId() throws Exception {
        AllocationDummy dummy = new AllocationDummy();
        Field idField = AllocationDummy.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(dummy, null);

        assertThat(idField.get(dummy)).isNull();
    }

    @Test
    void shouldHaveEntityAnnotation() {
        assertThat(AllocationDummy.class.isAnnotationPresent(jakarta.persistence.Entity.class)).isTrue();
    }

    @Test
    void shouldHaveTableAnnotation() {
        jakarta.persistence.Table tableAnnotation = AllocationDummy.class.getAnnotation(jakarta.persistence.Table.class);

        assertThat(tableAnnotation).isNotNull();
        assertThat(tableAnnotation.name()).isEqualTo("sys_no_rule");
    }

    @Test
    void shouldHaveIdFieldWithIdAnnotation() throws Exception {
        Field idField = AllocationDummy.class.getDeclaredField("id");

        assertThat(idField.isAnnotationPresent(jakarta.persistence.Id.class)).isTrue();
    }

    @Test
    void shouldSupportDifferentIdValues() throws Exception {
        AllocationDummy dummy1 = new AllocationDummy();
        AllocationDummy dummy2 = new AllocationDummy();
        Field idField = AllocationDummy.class.getDeclaredField("id");
        idField.setAccessible(true);

        idField.set(dummy1, 1L);
        idField.set(dummy2, 2L);

        assertThat(idField.get(dummy1)).isNotEqualTo(idField.get(dummy2));
    }
}