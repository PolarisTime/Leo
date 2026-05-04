package com.leo.erp.common.persistence;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class Specs {

    private Specs() {}

    public static <T> Specification<T> notDeleted() {
        return (root, q, cb) -> {
            if (currentUserIsAdmin()) {
                return cb.conjunction();
            }
            return cb.isFalse(root.get("deletedFlag"));
        };
    }

    private static boolean currentUserIsAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
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

    public static <T, V extends Comparable<? super V>> Specification<T> betweenIfPresent(String field, V start, V end) {
        return (root, q, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>(2);
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(field), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get(field), end));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
