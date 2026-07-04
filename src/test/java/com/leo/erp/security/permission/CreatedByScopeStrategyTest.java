package com.leo.erp.security.permission;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreatedByScopeStrategyTest {

    private final CreatedByScopeStrategy strategy = CreatedByScopeStrategy.instance();

    @Test
    void shouldReturnSingletonInstance() {
        assertThat(CreatedByScopeStrategy.instance()).isSameAs(CreatedByScopeStrategy.instance());
    }

    @Test
    void shouldReturnCreatedByOwnerField() {
        assertThat(strategy.ownerField()).isEqualTo("createdBy");
    }

    @Test
    void canAccessShouldReturnTrueWhenOwnerMatches() {
        AbstractAuditableEntity entity = mock(AbstractAuditableEntity.class);
        when(entity.getCreatedBy()).thenReturn(100L);

        assertThat(strategy.canAccess(entity, Set.of(100L))).isTrue();
    }

    @Test
    void canAccessShouldReturnFalseWhenOwnerDoesNotMatch() {
        AbstractAuditableEntity entity = mock(AbstractAuditableEntity.class);
        when(entity.getCreatedBy()).thenReturn(100L);

        assertThat(strategy.canAccess(entity, Set.of(200L))).isFalse();
    }

    @Test
    void canAccessShouldReturnFalseWhenEntityIsNull() {
        assertThat(strategy.canAccess(null, Set.of(100L))).isFalse();
    }

    @Test
    void canAccessShouldReturnFalseWhenOwnerIdsEmpty() {
        AbstractAuditableEntity entity = mock(AbstractAuditableEntity.class);
        assertThat(strategy.canAccess(entity, Set.of())).isFalse();
    }

    @Test
    void canAccessShouldReturnTrueWhenAnyOwnerMatches() {
        AbstractAuditableEntity entity = mock(AbstractAuditableEntity.class);
        when(entity.getCreatedBy()).thenReturn(100L);

        assertThat(strategy.canAccess(entity, Set.of(100L, 200L, 300L))).isTrue();
    }

    @Test
    void toSpecificationShouldReturnSpecification() {
        Specification<AbstractAuditableEntity> spec = strategy.toSpecification(Set.of(100L, 200L));
        assertThat(spec).isNotNull();
    }

    @Test
    void toSpecificationShouldReturnDisjunctionWhenEmpty() {
        Specification<AbstractAuditableEntity> spec = strategy.toSpecification(Set.of());
        assertThat(spec).isNotNull();
    }

    @Test
    void toSpecificationShouldExecuteDisjunctionPredicateWhenOwnerIdsEmpty() {
        Specification<AbstractAuditableEntity> spec = strategy.toSpecification(Set.of());
        Root<AbstractAuditableEntity> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        when(criteriaBuilder.disjunction()).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, query, criteriaBuilder);

        assertThat(result).isSameAs(predicate);
        verify(criteriaBuilder).disjunction();
        verifyNoInteractions(root);
    }

    @Test
    void toSpecificationShouldExecuteCreatedByInPredicateWhenOwnerIdsPresent() {
        Set<Long> ownerIds = Set.of(100L, 200L);
        Specification<AbstractAuditableEntity> spec = strategy.toSpecification(ownerIds);
        Root<AbstractAuditableEntity> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path<Long> createdByPath = mockPath();
        Predicate predicate = mock(Predicate.class);
        when(root.<Long>get("createdBy")).thenReturn(createdByPath);
        when(createdByPath.in(ownerIds)).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, query, criteriaBuilder);

        assertThat(result).isSameAs(predicate);
        verify(root).get("createdBy");
        verify(createdByPath).in(ownerIds);
        verifyNoInteractions(criteriaBuilder);
    }

    @SuppressWarnings("unchecked")
    private Root<AbstractAuditableEntity> mockRoot() {
        return mock(Root.class);
    }

    @SuppressWarnings("unchecked")
    private Path<Long> mockPath() {
        return mock(Path.class);
    }
}
