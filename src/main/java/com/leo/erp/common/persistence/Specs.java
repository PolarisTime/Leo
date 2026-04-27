package com.leo.erp.common.persistence;

import org.springframework.data.jpa.domain.Specification;

public final class Specs {

    private Specs() {}

    public static <T> Specification<T> notDeleted() {
        return (root, q, cb) -> cb.isFalse(root.get("deletedFlag"));
    }

    public static <T> Specification<T> keywordLike(String keyword, String... fields) {
        return (root, q, cb) -> {
            if (keyword == null || keyword.isBlank()) return cb.conjunction();
            String pattern = "%" + keyword.trim() + "%";
            var predicates = new jakarta.persistence.criteria.Predicate[fields.length];
            for (int i = 0; i < fields.length; i++) {
                predicates[i] = cb.like(root.get(fields[i]), pattern);
            }
            return cb.or(predicates);
        };
    }

    public static <T> Specification<T> equalIfPresent(String field, String value) {
        return (root, q, cb) -> {
            if (value == null || value.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(root.get(field), value.trim());
        };
    }
}
