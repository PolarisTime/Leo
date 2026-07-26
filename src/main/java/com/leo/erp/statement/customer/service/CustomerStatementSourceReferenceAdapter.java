package com.leo.erp.statement.customer.service;

import com.leo.erp.sales.api.SalesOrderStatementReferenceQuery;
import com.leo.erp.statement.api.CustomerStatementSourceReferenceQuery;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
public class CustomerStatementSourceReferenceAdapter
        implements CustomerStatementSourceReferenceQuery, SalesOrderStatementReferenceQuery {

    private final CustomerStatementRepository repository;

    public CustomerStatementSourceReferenceAdapter(CustomerStatementRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findActiveStatementIdsBySalesOrderItemIds(Collection<Long> salesOrderItemIds) {
        List<Long> stableItemIds = normalizeIds(salesOrderItemIds);
        if (stableItemIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(repository.findActiveStatementIdsBySourceSalesOrderItemIds(stableItemIds));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveCustomerStatementReferences(Collection<Long> salesOrderItemIds) {
        return !findActiveStatementIdsBySalesOrderItemIds(salesOrderItemIds).isEmpty();
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
