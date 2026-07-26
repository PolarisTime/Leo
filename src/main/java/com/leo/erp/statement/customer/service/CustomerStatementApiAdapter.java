package com.leo.erp.statement.customer.service;

import com.leo.erp.statement.api.CustomerStatementApi;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomerStatementApiAdapter implements CustomerStatementApi {

    private final CustomerStatementQueryService queryService;

    public CustomerStatementApiAdapter(CustomerStatementQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public Optional<Snapshot> findActiveById(Long statementId) {
        return queryService.findActiveById(statementId).map(this::toSnapshot);
    }

    @Override
    public Snapshot requireActiveById(Long statementId) {
        return toSnapshot(queryService.requireActiveById(statementId));
    }

    private Snapshot toSnapshot(CustomerStatement statement) {
        return new Snapshot(
                statement.getId(),
                statement.getStatementNo(),
                statement.getCustomerId(),
                statement.getCustomerCode(),
                statement.getCustomerName(),
                statement.getProjectId(),
                statement.getProjectName(),
                statement.getSettlementCompanyId(),
                statement.getSettlementCompanyName(),
                statement.getSalesAmount(),
                statement.getClosingAmount(),
                statement.getStatus()
        );
    }
}
