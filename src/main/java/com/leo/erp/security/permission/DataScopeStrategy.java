package com.leo.erp.security.permission;

import com.leo.erp.common.persistence.AuditableEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;

/**
 * Extension point for data scope filtering strategies.
 * <p>
 * The default strategy ({@link CreatedByScopeStrategy}) filters by {@code createdBy}
 * which matches the current ERP data model. When business requirements evolve to
 * filter by salesperson, purchaser, department, or warehouse, implement this interface
 * and register via {@link DataScopeContext#setStrategy(DataScopeStrategy)}.
 * </p>
 */
public interface DataScopeStrategy<E extends AuditableEntity> {

    /**
     * Build a JPA Specification that restricts rows to the given owner user IDs.
     */
    Specification<E> toSpecification(Set<Long> ownerUserIds);

    /**
     * Check whether the given entity is accessible under the owner user ID set.
     */
    boolean canAccess(E entity, Set<Long> ownerUserIds);

    /**
     * Returns the entity field name used for ownership filtering (e.g. "createdBy").
     */
    String ownerField();
}
