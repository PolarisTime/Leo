package com.leo.erp.statement.service;

import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class StatementCandidateSupport {

    private StatementCandidateSupport() {
    }

    public static Set<String> parseRelationNos(Collection<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        rawValues.stream()
                .filter(Objects::nonNull)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(result::add);
        return result;
    }

    public static <T> Specification<T> excludeFieldValues(String fieldName, Set<String> excludedValues) {
        return (root, query, criteriaBuilder) -> {
            if (fieldName == null || fieldName.isBlank() || excludedValues == null || excludedValues.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.not(root.get(fieldName).in(excludedValues));
        };
    }
}
