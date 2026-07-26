package com.leo.erp.statement.api;

import java.math.BigDecimal;
import java.util.Optional;

public interface CustomerStatementApi {

    Optional<Snapshot> findActiveById(Long statementId);

    Snapshot requireActiveById(Long statementId);

    record Snapshot(
            Long id,
            String statementNo,
            Long customerId,
            String customerCode,
            String customerName,
            Long projectId,
            String projectName,
            Long settlementCompanyId,
            String settlementCompanyName,
            BigDecimal salesAmount,
            BigDecimal closingAmount,
            String status
    ) {
    }
}
