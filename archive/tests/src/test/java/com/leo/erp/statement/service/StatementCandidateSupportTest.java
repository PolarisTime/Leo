package com.leo.erp.statement.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StatementCandidateSupportTest {

    @Test
    void shouldParseCommaSeparatedValues() {
        Collection<String> rawValues = List.of("SO-001,SO-002", "SO-003");

        Set<String> result = StatementCandidateSupport.parseRelationNos(rawValues);

        assertThat(result).containsExactly("SO-001", "SO-002", "SO-003");
    }

    @Test
    void shouldTrimWhitespace() {
        Collection<String> rawValues = List.of("  SO-001 , SO-002  ");

        Set<String> result = StatementCandidateSupport.parseRelationNos(rawValues);

        assertThat(result).containsExactly("SO-001", "SO-002");
    }

    @Test
    void shouldFilterEmptyStrings() {
        Collection<String> rawValues = List.of("SO-001,,", ",SO-002");

        Set<String> result = StatementCandidateSupport.parseRelationNos(rawValues);

        assertThat(result).containsExactly("SO-001", "SO-002");
    }

    @Test
    void shouldFilterNullValues() {
        Collection<String> rawValues = java.util.Arrays.asList(null, "SO-001", null);

        Set<String> result = StatementCandidateSupport.parseRelationNos(rawValues);

        assertThat(result).containsExactly("SO-001");
    }

    @Test
    void shouldReturnEmptySetForNullInput() {
        Set<String> result = StatementCandidateSupport.parseRelationNos(null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptySetForEmptyInput() {
        Set<String> result = StatementCandidateSupport.parseRelationNos(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldDeduplicateValues() {
        Collection<String> rawValues = List.of("SO-001,SO-001", "SO-001");

        Set<String> result = StatementCandidateSupport.parseRelationNos(rawValues);

        assertThat(result).hasSize(1);
        assertThat(result).containsExactly("SO-001");
    }

    @ParameterizedTest
    @MethodSource("invalidExclusionInputs")
    void shouldReturnConjunctionWhenExclusionInputIsInvalid(String fieldName, Set<String> excludedValues) {
        Root<Object> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Predicate expected = mock(Predicate.class);
        Specification<Object> specification = StatementCandidateSupport.excludeFieldValues(fieldName, excludedValues);
        when(criteriaBuilder.conjunction()).thenReturn(expected);

        Predicate result = specification.toPredicate(root, query, criteriaBuilder);

        assertThat(result).isSameAs(expected);
        verify(criteriaBuilder).conjunction();
        verifyNoInteractions(root);
    }

    @Test
    void shouldBuildNotInPredicateForCandidateStatusValues() {
        Root<Object> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path<Object> statusPath = mockPath();
        Predicate inPredicate = mock(Predicate.class);
        Predicate expected = mock(Predicate.class);
        Set<String> excludedValues = Set.of("CANDIDATE");
        Specification<Object> specification = StatementCandidateSupport.excludeFieldValues("status", excludedValues);
        when(root.get("status")).thenReturn(statusPath);
        when(statusPath.in(excludedValues)).thenReturn(inPredicate);
        when(criteriaBuilder.not(inPredicate)).thenReturn(expected);

        Predicate result = specification.toPredicate(root, query, criteriaBuilder);

        assertThat(result).isSameAs(expected);
        verify(root).get("status");
        verify(statusPath).in(excludedValues);
        verify(criteriaBuilder).not(inPredicate);
    }

    private static Stream<Arguments> invalidExclusionInputs() {
        return Stream.of(
                Arguments.of(null, Set.of("CANDIDATE")),
                Arguments.of("   ", Set.of("CANDIDATE")),
                Arguments.of("status", null),
                Arguments.of("status", Set.of())
        );
    }

    @SuppressWarnings("unchecked")
    private Root<Object> mockRoot() {
        return mock(Root.class);
    }

    @SuppressWarnings("unchecked")
    private Path<Object> mockPath() {
        return mock(Path.class);
    }
}
