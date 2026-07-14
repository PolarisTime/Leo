package com.leo.erp.security.permission;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataScopeStrategyTest {

    @Test
    void shouldDefineRequiredMethods() {
        DataScopeStrategy<AbstractAuditableEntity> strategy = new DataScopeStrategy<>() {
            @Override
            public Specification<AbstractAuditableEntity> toSpecification(Set<Long> ownerUserIds) {
                return null;
            }

            @Override
            public boolean canAccess(AbstractAuditableEntity entity, Set<Long> ownerUserIds) {
                return false;
            }

            @Override
            public String ownerField() {
                return "testField";
            }
        };

        assertThat(strategy.ownerField()).isEqualTo("testField");
        assertThat(strategy.canAccess(mock(AbstractAuditableEntity.class), Set.of())).isFalse();
    }

    @Test
    void createdByScopeStrategyShouldImplementInterface() {
        assertThat(DataScopeStrategy.class.isAssignableFrom(CreatedByScopeStrategy.class)).isTrue();
    }

    @Test
    void shouldSupportCustomImplementation() {
        DataScopeStrategy<AbstractAuditableEntity> customStrategy = new DataScopeStrategy<>() {
            @Override
            public Specification<AbstractAuditableEntity> toSpecification(Set<Long> ownerUserIds) {
                return (root, query, cb) -> cb.disjunction();
            }

            @Override
            public boolean canAccess(AbstractAuditableEntity entity, Set<Long> ownerUserIds) {
                return entity != null && ownerUserIds.contains(entity.getCreatedBy());
            }

            @Override
            public String ownerField() {
                return "customOwner";
            }
        };

        AbstractAuditableEntity entity = mock(AbstractAuditableEntity.class);
        when(entity.getCreatedBy()).thenReturn(1L);

        assertThat(customStrategy.canAccess(entity, Set.of(1L))).isTrue();
        assertThat(customStrategy.ownerField()).isEqualTo("customOwner");
    }
}
