package com.leo.erp.statement.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
}
