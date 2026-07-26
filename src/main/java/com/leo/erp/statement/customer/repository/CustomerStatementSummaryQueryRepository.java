package com.leo.erp.statement.customer.repository;

import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
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
public class CustomerStatementSummaryQueryRepository {

    private final EntityManager entityManager;

    public CustomerStatementSummaryQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public CustomerStatementSummaryAggregate summarize(Specification<CustomerStatement> specification) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<CustomerStatement> root = query.from(CustomerStatement.class);
        query.multiselect(
                builder.count(root),
                builder.coalesce(builder.sum(root.<BigDecimal>get("salesAmount")), BigDecimal.ZERO),
                builder.coalesce(builder.sum(root.<BigDecimal>get("receiptAmount")), BigDecimal.ZERO),
                builder.coalesce(builder.sum(root.<BigDecimal>get("closingAmount")), BigDecimal.ZERO)
        );
        Predicate predicate = specification == null ? null : specification.toPredicate(root, query, builder);
        if (predicate != null) {
            query.where(predicate);
        }
        Tuple result = entityManager.createQuery(query).getSingleResult();
        return new CustomerStatementSummaryAggregate(
                result.get(0, Long.class),
                result.get(1, BigDecimal.class),
                result.get(2, BigDecimal.class),
                result.get(3, BigDecimal.class)
        );
    }
}
