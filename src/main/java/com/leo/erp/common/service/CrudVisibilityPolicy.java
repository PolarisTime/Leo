package com.leo.erp.common.service;

import org.springframework.data.jpa.domain.Specification;

public class CrudVisibilityPolicy {

    public <E> Specification<E> applyDeletedVisibility(Specification<E> specification,
                                                       boolean shouldViewDeletedRecords) {
        if (shouldViewDeletedRecords) {
            return specification;
        }
        Specification<E> activeOnly = (root, query, criteriaBuilder) ->
                criteriaBuilder.isFalse(root.get("deletedFlag"));
        return specification == null ? activeOnly : specification.and(activeOnly);
    }

}
