package com.leo.erp.common.persistence;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractAuditableEntityTest {

    private static class TestEntity extends AbstractAuditableEntity {}

    @Test
    void defaultValues() {
        TestEntity entity = new TestEntity();
        assertThat(entity.getCreatedBy()).isNull();
        assertThat(entity.getCreatedName()).isEqualTo("system");
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedBy()).isNull();
        assertThat(entity.getUpdatedName()).isEqualTo("system");
        assertThat(entity.getUpdatedAt()).isNull();
        assertThat(entity.isDeletedFlag()).isFalse();
    }

    @Test
    void settersAndGetters() {
        TestEntity entity = new TestEntity();
        LocalDateTime now = LocalDateTime.now();

        entity.setCreatedBy(100L);
        entity.setCreatedName("admin");
        entity.setCreatedAt(now);
        entity.setUpdatedBy(200L);
        entity.setUpdatedName("editor");
        entity.setUpdatedAt(now);
        entity.setDeletedFlag(true);

        assertThat(entity.getCreatedBy()).isEqualTo(100L);
        assertThat(entity.getCreatedName()).isEqualTo("admin");
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedBy()).isEqualTo(200L);
        assertThat(entity.getUpdatedName()).isEqualTo("editor");
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
        assertThat(entity.isDeletedFlag()).isTrue();
    }

    @Test
    void deletedFlag_defaultFalse() {
        TestEntity entity = new TestEntity();
        assertThat(entity.isDeletedFlag()).isFalse();
    }
}
