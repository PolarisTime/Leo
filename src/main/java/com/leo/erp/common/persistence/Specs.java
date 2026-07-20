package com.leo.erp.common.persistence;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class Specs {

    private static final String DELETED_DOCUMENT_STATUS = "已删除";

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

    public static <T> Specification<T> equalValueIfPresent(String field, Object value) {
        return (root, q, cb) -> {
            if (value == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get(field), value);
        };
    }

    /**
     * 单据列表将“已删除”作为删除标记筛选值展示，业务状态字段仍保留删除前的真实状态。
     */
    public static <T> Specification<T> documentStatus(String selectedStatus) {
        return (root, q, cb) -> {
            String normalizedStatus = selectedStatus == null ? "" : selectedStatus.trim();
            if (DELETED_DOCUMENT_STATUS.equals(normalizedStatus)) {
                return cb.isTrue(root.get("deletedFlag"));
            }

            Predicate activeOnly = cb.isFalse(root.get("deletedFlag"));
            if (normalizedStatus.isEmpty()) {
                return activeOnly;
            }
            return cb.and(activeOnly, cb.equal(root.get("status"), normalizedStatus));
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

    public static <T> Specification<T> dateTimeBetweenDatesIfPresent(String field, LocalDate start, LocalDate end) {
        return (root, q, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>(2);
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(field), start.atStartOfDay()));
            }
            if (end != null) {
                predicates.add(cb.lessThan(root.get(field), end.plusDays(1).atStartOfDay()));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
