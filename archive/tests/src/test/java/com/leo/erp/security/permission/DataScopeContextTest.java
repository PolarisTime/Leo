package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.AbstractAuditableEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataScopeContextTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
        DataScopeContext.setStrategy(CreatedByScopeStrategy.instance());
    }

    @Test
    void shouldAllowAllScopeToAccessAnyEntity() {
        DataScopeContext.set(1L, "material", "all");
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(2L);

        assertThatCode(() -> DataScopeContext.assertCanAccess(entity)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectSelfScopeWhenEntityBelongsToAnotherUser() {
        DataScopeContext.set(1L, "material", "self");
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(2L);

        assertThatThrownBy(() -> DataScopeContext.assertCanAccess(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldAllowDepartmentScopeWhenEntityBelongsToSameDepartmentUser() {
        DataScopeContext.set(1L, "material", "department", Set.of(1L, 2L));
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(2L);

        assertThatCode(() -> DataScopeContext.assertCanAccess(entity)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDepartmentScopeWhenEntityBelongsToAnotherDepartment() {
        DataScopeContext.set(1L, "material", "department", Set.of(1L, 3L));
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(2L);

        assertThatThrownBy(() -> DataScopeContext.assertCanAccess(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldReportOwnerFilterAndAllowNullEntity() {
        DataScopeContext.set(1L, "material", "self");

        assertThat(DataScopeContext.hasOwnerFilter()).isTrue();
        assertThat(DataScopeContext.canAccess(null)).isTrue();
    }

    @Test
    void shouldReportNoOwnerFilterWhenContextIsMissingOrScopeIsAll() {
        assertThat(DataScopeContext.hasOwnerFilter()).isFalse();

        DataScopeContext.set(1L, "material", "all");

        assertThat(DataScopeContext.hasOwnerFilter()).isFalse();
    }

    @Test
    void shouldRejectAccessWhenExplicitOwnerSetIsEmpty() {
        DataScopeContext.set(1L, "material", "department", Set.of());
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(1L);

        assertThat(DataScopeContext.canAccess(entity)).isFalse();
        assertThatThrownBy(() -> DataScopeContext.assertCanAccess(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldDefaultOwnerIdsToCurrentUserWhenExplicitOwnersAreNull() {
        DataScopeContext.set(5L, "material", "department", null);

        assertThat(DataScopeContext.allowedOwnerUserIds()).containsExactly(5L);
    }

    @Test
    void shouldNotApplyOwnerFilterWhenUserIdIsMissing() {
        DataScopeContext.set(null, "material", "self");

        assertThat(DataScopeContext.allowedOwnerUserIds()).isNull();
        assertThat(DataScopeContext.hasOwnerFilter()).isFalse();
    }

    @Test
    void shouldRejectReservedScopeWithoutExplicitOwners() {
        assertThatThrownBy(() -> DataScopeContext.set(1L, "material", "department"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("custom scope is reserved");
    }

    @Test
    void shouldUseCustomStrategyWhenApplyingOwnerFilter() {
        Specification<TestEntity> ownerSpec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        DataScopeStrategy<TestEntity> customStrategy = new DataScopeStrategy<TestEntity>() {
            @Override
            public Specification<TestEntity> toSpecification(Set<Long> ownerUserIds) {
                return ownerSpec;
            }

            @Override
            public boolean canAccess(TestEntity entity, Set<Long> ownerUserIds) {
                return true;
            }

            @Override
            public String ownerField() {
                return "ownerId";
            }
        };
        DataScopeContext.setStrategy(customStrategy);
        DataScopeContext.set(5L, "material", "self");

        Specification<TestEntity> result = DataScopeContext.apply(null);

        assertThat(result).isSameAs(ownerSpec);
    }

    @Test
    void shouldCombineExistingSpecificationWithOwnerSpecification() {
        Specification<TestEntity> existing = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        DataScopeContext.set(5L, "material", "self");

        Specification<TestEntity> result = DataScopeContext.apply(existing);

        assertThat(result).isNotNull().isNotSameAs(existing);
    }

    private static class TestEntity extends AbstractAuditableEntity {
    }
}
