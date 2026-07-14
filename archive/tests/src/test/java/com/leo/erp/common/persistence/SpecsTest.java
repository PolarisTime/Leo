package com.leo.erp.common.persistence;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.any;

class SpecsTest {

    @SuppressWarnings("unchecked")
    private final Root<Object> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

    @Test
    void notDeleted_createsSpecification() {
        var path = mock(jakarta.persistence.criteria.Path.class);
        when(root.get("deletedFlag")).thenReturn(path);
        when(cb.isFalse(path)).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.notDeleted();
        spec.toPredicate(root, query, cb);

        verify(root).get("deletedFlag");
        verify(cb).isFalse(path);
    }

    @Test
    void keywordLike_withMultipleFields_createsOrPredicates() {
        var path1 = mock(jakarta.persistence.criteria.Path.class);
        var path2 = mock(jakarta.persistence.criteria.Path.class);
        when(root.get("name")).thenReturn(path1);
        when(root.get("code")).thenReturn(path2);
        when(cb.like(path1, "%test%")).thenReturn(mock(Predicate.class));
        when(cb.like(path2, "%test%")).thenReturn(mock(Predicate.class));
        when(cb.or(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.keywordLike("test", "name", "code");
        spec.toPredicate(root, query, cb);

        verify(cb).like(path1, "%test%");
        verify(cb).like(path2, "%test%");
    }

    @Test
    void keywordLike_nullKeyword_returnsConjunction() {
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.keywordLike(null, "name");
        Predicate result = spec.toPredicate(root, query, cb);

        verify(cb).conjunction();
        assertThat(result).isNotNull();
    }

    @Test
    void keywordLike_blankKeyword_returnsConjunction() {
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.keywordLike("  ", "name");
        spec.toPredicate(root, query, cb);

        verify(cb).conjunction();
    }

    @Test
    void keywordLike_trimsKeyword() {
        var path = mock(jakarta.persistence.criteria.Path.class);
        when(root.get("name")).thenReturn(path);
        when(cb.like(path, "%hello%")).thenReturn(mock(Predicate.class));
        when(cb.or(any(Predicate[].class))).thenAnswer(inv -> inv.getArgument(0));

        Specification<Object> spec = Specs.keywordLike("  hello  ", "name");
        spec.toPredicate(root, query, cb);

        verify(cb).like(path, "%hello%");
    }

    @Test
    void equalIfPresent_withValue_returnsEqualPredicate() {
        var path = mock(jakarta.persistence.criteria.Path.class);
        when(root.get("status")).thenReturn(path);
        when(cb.equal(path, "ACTIVE")).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.equalIfPresent("status", "ACTIVE");
        spec.toPredicate(root, query, cb);

        verify(cb).equal(path, "ACTIVE");
    }

    @Test
    void equalIfPresent_nullValue_returnsConjunction() {
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.equalIfPresent("status", null);
        spec.toPredicate(root, query, cb);

        verify(cb).conjunction();
    }

    @Test
    void equalIfPresent_blankValue_returnsConjunction() {
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.equalIfPresent("status", "  ");
        spec.toPredicate(root, query, cb);

        verify(cb).conjunction();
    }

    @Test
    void equalIfPresent_trimsValue() {
        var path = mock(jakarta.persistence.criteria.Path.class);
        when(root.get("status")).thenReturn(path);
        when(cb.equal(path, "ACTIVE")).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.equalIfPresent("status", "  ACTIVE  ");
        spec.toPredicate(root, query, cb);

        verify(cb).equal(path, "ACTIVE");
    }

    @Test
    void equalValueIfPresent_nullValue_returnsConjunction() {
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.equalValueIfPresent("status", null);
        Predicate result = spec.toPredicate(root, query, cb);

        verify(cb).conjunction();
        assertThat(result).isNotNull();
    }

    @Test
    void equalValueIfPresent_withValue_returnsEqualPredicate() {
        var path = mock(jakarta.persistence.criteria.Path.class);
        when(root.get("status")).thenReturn(path);
        when(cb.equal(path, 1)).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.equalValueIfPresent("status", 1);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(path, 1);
    }

    @Test
    void betweenIfPresent_bothPresent_returnsAndPredicate() {
        var path = mock(jakarta.persistence.criteria.Path.class);
        when(root.get("amount")).thenReturn(path);
        var ge = mock(Predicate.class);
        var le = mock(Predicate.class);
        when(cb.greaterThanOrEqualTo(path, 10)).thenReturn(ge);
        when(cb.lessThanOrEqualTo(path, 100)).thenReturn(le);
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.betweenIfPresent("amount", 10, 100);
        spec.toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(path, 10);
        verify(cb).lessThanOrEqualTo(path, 100);
    }

    @Test
    void betweenIfPresent_startNull_onlyAppliesLessThan() {
        var path = mock(jakarta.persistence.criteria.Path.class);
        when(root.get("amount")).thenReturn(path);
        var le = mock(Predicate.class);
        when(cb.lessThanOrEqualTo(path, 100)).thenReturn(le);

        Specification<Object> spec = Specs.betweenIfPresent("amount", null, 100);
        spec.toPredicate(root, query, cb);

        verify(cb).lessThanOrEqualTo(path, 100);
    }

    @Test
    void betweenIfPresent_endNull_onlyAppliesGreaterThan() {
        var path = mock(jakarta.persistence.criteria.Path.class);
        when(root.get("amount")).thenReturn(path);
        var ge = mock(Predicate.class);
        when(cb.greaterThanOrEqualTo(path, 10)).thenReturn(ge);

        Specification<Object> spec = Specs.betweenIfPresent("amount", 10, null);
        spec.toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(path, 10);
    }

    @Test
    void betweenIfPresent_bothNull_returnsConjunction() {
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.betweenIfPresent("amount", null, null);
        spec.toPredicate(root, query, cb);

        verify(cb).conjunction();
    }

    @Test
    void dateTimeBetweenDatesIfPresent_usesHalfOpenEndDateBoundary() {
        var path = mock(jakarta.persistence.criteria.Path.class);
        when(root.get("orderDate")).thenReturn(path);
        LocalDate startDate = LocalDate.of(2026, 7, 8);
        LocalDate endDate = LocalDate.of(2026, 7, 8);
        LocalDateTime startInclusive = LocalDateTime.of(2026, 7, 8, 0, 0);
        LocalDateTime endExclusive = LocalDateTime.of(2026, 7, 9, 0, 0);
        when(cb.greaterThanOrEqualTo(path, startInclusive)).thenReturn(mock(Predicate.class));
        when(cb.lessThan(path, endExclusive)).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        Specification<Object> spec = Specs.dateTimeBetweenDatesIfPresent("orderDate", startDate, endDate);
        spec.toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(path, startInclusive);
        verify(cb).lessThan(path, endExclusive);
    }
}
