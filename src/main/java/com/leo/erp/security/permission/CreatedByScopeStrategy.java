package com.leo.erp.security.permission;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.Objects;
import java.util.Set;

public final class CreatedByScopeStrategy implements DataScopeStrategy<AbstractAuditableEntity> {

    private static final CreatedByScopeStrategy INSTANCE = new CreatedByScopeStrategy();

    private CreatedByScopeStrategy() {
    }

    public static CreatedByScopeStrategy instance() {
        return INSTANCE;
    }

    @Override
    public Specification<AbstractAuditableEntity> toSpecification(Set<Long> ownerUserIds) {
        return (root, query, criteriaBuilder) ->
                ownerUserIds.isEmpty()
                        ? criteriaBuilder.disjunction()
                        : root.get("createdBy").in(ownerUserIds);
    }

    @Override
    public boolean canAccess(AbstractAuditableEntity entity, Set<Long> ownerUserIds) {
        if (entity == null || ownerUserIds.isEmpty()) {
            return false;
        }
        return ownerUserIds.stream().anyMatch(ownerId -> Objects.equals(ownerId, entity.getCreatedBy()));
    }

    @Override
    public String ownerField() {
        return "createdBy";
    }
}
