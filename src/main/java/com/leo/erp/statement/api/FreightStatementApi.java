package com.leo.erp.statement.api;

import java.math.BigDecimal;
import java.util.Optional;

public interface FreightStatementApi {

    Optional<Snapshot> findActiveById(Long statementId);

    Snapshot requireActiveById(Long statementId);

    record Snapshot(
            Long id,
            String statementNo,
            Long carrierId,
            String carrierCode,
            String carrierName,
            Long settlementCompanyId,
            String settlementCompanyName,
            BigDecimal totalFreight,
            BigDecimal unpaidAmount,
            String status
    ) {
    }
}
