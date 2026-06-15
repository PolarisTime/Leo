package com.leo.erp.common.service;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

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

    private static class TestEntity extends AbstractAuditableEntity {
    }
}
