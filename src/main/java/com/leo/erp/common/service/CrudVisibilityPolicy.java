package com.leo.erp.common.service;

import org.springframework.data.jpa.domain.Specification;

import java.util.Set;

public class CrudVisibilityPolicy {

    public <E> Specification<E> applyListVisibility(Specification<E> specification,
                                                    CrudRuntimeSettings crudRuntimeSettings) {
        if (crudRuntimeSettings == null) {
            return specification;
        }
        Set<String> hiddenStatuses = crudRuntimeSettings.getHiddenAuditedStatuses();
        if (hiddenStatuses.isEmpty()) {
            return specification;
        }
        return combine(specification, excludeStatuses(hiddenStatuses));
    }

    public <E> Specification<E> applyDeletedVisibility(Specification<E> specification,
                                                       boolean shouldViewDeletedRecords) {
        if (shouldViewDeletedRecords) {
            return specification;
        }
        Specification<E> activeOnly = (root, query, criteriaBuilder) ->
                criteriaBuilder.isFalse(root.get("deletedFlag"));
        return specification == null ? activeOnly : specification.and(activeOnly);
    }

    private <E> Specification<E> excludeStatuses(Set<String> hiddenStatuses) {
        return (root, query, criteriaBuilder) -> {
            try {
                root.getModel().getAttribute("status");
            } catch (IllegalArgumentException ex) {
                return criteriaBuilder.conjunction();
            }
            var statusPath = root.get("status");
            return criteriaBuilder.or(
                    criteriaBuilder.isNull(statusPath),
                    criteriaBuilder.not(statusPath.in(hiddenStatuses))
            );
        };
    }

    private <E> Specification<E> combine(Specification<E> left, Specification<E> right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.and(right);
    }
}
