package com.leo.erp.common.service;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrudVisibilityPolicyTest {

    private final CrudVisibilityPolicy policy = new CrudVisibilityPolicy();

    @Test
    void shouldReturnOriginalSpecificationWhenNoRuntimeSettings() {
        Specification<TestEntity> spec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        Specification<TestEntity> result = policy.applyListVisibility(spec, null);

        assertThat(result).isSameAs(spec);
    }

    @Test
    void shouldReturnOriginalSpecificationWhenHiddenStatusesEmpty() {
        Specification<TestEntity> spec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        CrudRuntimeSettings settings = mock(CrudRuntimeSettings.class);
        when(settings.getHiddenAuditedStatuses()).thenReturn(Set.of());

        Specification<TestEntity> result = policy.applyListVisibility(spec, settings);

        assertThat(result).isSameAs(spec);
    }

    @Test
    void shouldAddListVisibilitySpecificationWhenHiddenStatusesConfigured() {
        CrudRuntimeSettings settings = mock(CrudRuntimeSettings.class);
        when(settings.getHiddenAuditedStatuses()).thenReturn(Set.of("已审核"));

        Specification<TestEntity> result = policy.applyListVisibility(null, settings);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldCombineBaseSpecificationWhenHiddenStatusesConfigured() {
        Specification<TestEntity> baseSpec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        CrudRuntimeSettings settings = mock(CrudRuntimeSettings.class);
        when(settings.getHiddenAuditedStatuses()).thenReturn(Set.of("已审核"));

        Specification<TestEntity> result = policy.applyListVisibility(baseSpec, settings);

        assertThat(result).isNotNull();
        assertThat(result).isNotSameAs(baseSpec);
    }

    @Test
    void combineShouldReturnLeftWhenRightSpecificationIsNull() {
        Specification<TestEntity> left = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        @SuppressWarnings("unchecked")
        Specification<TestEntity> result = ReflectionTestUtils.invokeMethod(policy, "combine", left, null);

        assertThat(result).isSameAs(left);
    }

    @Test
    void shouldBuildHiddenStatusPredicateWhenStatusAttributeExists() {
        Set<String> hiddenStatuses = Set.of("已审核");
        CrudRuntimeSettings settings = mock(CrudRuntimeSettings.class);
        when(settings.getHiddenAuditedStatuses()).thenReturn(hiddenStatuses);
        Specification<TestEntity> spec = policy.applyListVisibility(null, settings);
        Root<TestEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        @SuppressWarnings("rawtypes")
        EntityType model = mock(EntityType.class);
        @SuppressWarnings("rawtypes")
        Attribute statusAttribute = mock(Attribute.class);
        Path<String> statusPath = mock(Path.class);
        Predicate isNull = mock(Predicate.class);
        Predicate inHiddenStatuses = mock(Predicate.class);
        Predicate notHiddenStatuses = mock(Predicate.class);
        Predicate expected = mock(Predicate.class);
        when(root.getModel()).thenReturn(model);
        when(model.getAttribute("status")).thenReturn(statusAttribute);
        when(root.<String>get("status")).thenReturn(statusPath);
        when(criteriaBuilder.isNull(statusPath)).thenReturn(isNull);
        when(statusPath.in(hiddenStatuses)).thenReturn(inHiddenStatuses);
        when(criteriaBuilder.not(inHiddenStatuses)).thenReturn(notHiddenStatuses);
        when(criteriaBuilder.or(isNull, notHiddenStatuses)).thenReturn(expected);

        assertThat(spec.toPredicate(root, query, criteriaBuilder)).isSameAs(expected);
    }

    @Test
    void shouldUseConjunctionWhenEntityDoesNotHaveStatusAttribute() {
        CrudRuntimeSettings settings = mock(CrudRuntimeSettings.class);
        when(settings.getHiddenAuditedStatuses()).thenReturn(Set.of("已审核"));
        Specification<TestEntity> spec = policy.applyListVisibility(null, settings);
        Root<TestEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        @SuppressWarnings("rawtypes")
        EntityType model = mock(EntityType.class);
        Predicate expected = mock(Predicate.class);
        when(root.getModel()).thenReturn(model);
        when(model.getAttribute("status")).thenThrow(new IllegalArgumentException("missing"));
        when(criteriaBuilder.conjunction()).thenReturn(expected);

        assertThat(spec.toPredicate(root, query, criteriaBuilder)).isSameAs(expected);
    }

    @Test
    void shouldKeepDeletedRecordsWhenAllowed() {
        Specification<TestEntity> spec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        Specification<TestEntity> result = policy.applyDeletedVisibility(spec, true);

        assertThat(result).isSameAs(spec);
    }

    @Test
    void shouldAddActiveOnlySpecificationWhenDeletedRecordsNotAllowed() {
        Specification<TestEntity> result = policy.applyDeletedVisibility(null, false);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldBuildActiveOnlyPredicateWhenDeletedRecordsNotAllowed() {
        Specification<TestEntity> spec = policy.applyDeletedVisibility(null, false);
        Root<TestEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path<Boolean> deletedPath = mock(Path.class);
        Predicate expected = mock(Predicate.class);
        when(root.<Boolean>get("deletedFlag")).thenReturn(deletedPath);
        when(criteriaBuilder.isFalse(deletedPath)).thenReturn(expected);

        assertThat(spec.toPredicate(root, query, criteriaBuilder)).isSameAs(expected);
    }

    private static class TestEntity extends AbstractAuditableEntity {
    }
}
