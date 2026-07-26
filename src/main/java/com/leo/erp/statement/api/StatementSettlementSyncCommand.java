package com.leo.erp.statement.api;

public interface StatementSettlementSyncCommand {

    void syncCustomerStatement(Long statementId);

    void syncFreightStatement(Long statementId);
}
