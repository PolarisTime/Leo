package com.leo.erp.statement.freight.repository;

import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public class FreightStatementSummaryQueryRepository {

    private final EntityManager entityManager;

    public FreightStatementSummaryQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public FreightStatementSummaryAggregate summarize(Specification<FreightStatement> specification) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<FreightStatement> root = query.from(FreightStatement.class);
        query.multiselect(
                builder.count(root),
                builder.coalesce(builder.sum(root.<BigDecimal>get("totalWeight")), BigDecimal.ZERO),
                builder.coalesce(builder.sum(root.<BigDecimal>get("totalFreight")), BigDecimal.ZERO),
                builder.coalesce(builder.sum(root.<BigDecimal>get("paidAmount")), BigDecimal.ZERO),
                builder.coalesce(builder.sum(root.<BigDecimal>get("unpaidAmount")), BigDecimal.ZERO)
        );
        Predicate predicate = specification == null ? null : specification.toPredicate(root, query, builder);
        if (predicate != null) {
            query.where(predicate);
        }
        Tuple result = entityManager.createQuery(query).getSingleResult();
        return new FreightStatementSummaryAggregate(
                result.get(0, Long.class),
                result.get(1, BigDecimal.class),
                result.get(2, BigDecimal.class),
                result.get(3, BigDecimal.class),
                result.get(4, BigDecimal.class)
        );
    }
}
