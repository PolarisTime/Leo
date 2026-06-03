package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagedEntityItemSupportTest {

    static class TestEntity {
        Long id;
        String name;

        TestEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        Long id() { return id; }
        String name() { return name; }
    }
    record TestRequest(Long id, String name) {}

    @Test
    void shouldCreateNewItems() {
        List<TestEntity> existing = new ArrayList<>();
        List<TestRequest> requests = List.of(new TestRequest(null, "Item1"), new TestRequest(null, "Item2"));

        List<TestEntity> result = ManagedEntityItemSupport.syncById(
                existing,
                requests,
                TestEntity::id,
                TestRequest::id,
                () -> new TestEntity(null, null),
                () -> 100L,
                (entity, id) -> entity.id = id
        );

        assertThat(result).hasSize(2);
        assertThat(existing).hasSize(2);
    }

    @Test
    void shouldRetainExistingItemsById() {
        List<TestEntity> existing = new ArrayList<>(List.of(new TestEntity(1L, "Old")));
        List<TestRequest> requests = List.of(new TestRequest(1L, "Updated"));

        List<TestEntity> result = ManagedEntityItemSupport.syncById(
                existing,
                requests,
                TestEntity::id,
                TestRequest::id,
                () -> new TestEntity(null, null),
                () -> 100L,
                (entity, id) -> entity.id = id
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(1L);
    }

    @Test
    void shouldRemoveItemsNotInRequest() {
        List<TestEntity> existing = new ArrayList<>(List.of(
                new TestEntity(1L, "Keep"),
                new TestEntity(2L, "Remove")
        ));
        List<TestRequest> requests = List.of(new TestRequest(1L, "Keep"));

        ManagedEntityItemSupport.syncById(
                existing,
                requests,
                TestEntity::id,
                TestRequest::id,
                () -> new TestEntity(null, null),
                () -> 100L,
                (entity, id) -> entity.id = id
        );

        assertThat(existing).hasSize(1);
        assertThat(existing.getFirst().id()).isEqualTo(1L);
    }

    @Test
    void shouldThrowOnDuplicateRequestId() {
        List<TestEntity> existing = new ArrayList<>(List.of(new TestEntity(1L, "Existing")));
        List<TestRequest> requests = List.of(
                new TestRequest(1L, "Dup"),
                new TestRequest(1L, "Dup")
        );

        assertThatThrownBy(() -> ManagedEntityItemSupport.syncById(
                existing,
                requests,
                TestEntity::id,
                TestRequest::id,
                () -> new TestEntity(null, null),
                () -> 100L,
                (entity, id) -> entity.id = id
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("ID重复");
    }

    @Test
    void shouldThrowOnNonExistentRequestId() {
        List<TestEntity> existing = new ArrayList<>();
        List<TestRequest> requests = List.of(new TestRequest(999L, "Fake"));

        assertThatThrownBy(() -> ManagedEntityItemSupport.syncById(
                existing,
                requests,
                TestEntity::id,
                TestRequest::id,
                () -> new TestEntity(null, null),
                () -> 100L,
                (entity, id) -> entity.id = id
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("ID不存在");
    }

    @Test
    void shouldMaintainRequestOrder() {
        List<TestEntity> existing = new ArrayList<>(List.of(
                new TestEntity(1L, "First"),
                new TestEntity(2L, "Second")
        ));
        List<TestRequest> requests = List.of(
                new TestRequest(2L, "Second"),
                new TestRequest(1L, "First")
        );

        List<TestEntity> result = ManagedEntityItemSupport.syncById(
                existing,
                requests,
                TestEntity::id,
                TestRequest::id,
                () -> new TestEntity(null, null),
                () -> 100L,
                (entity, id) -> entity.id = id
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(2L);
        assertThat(result.get(1).id()).isEqualTo(1L);
    }
}
