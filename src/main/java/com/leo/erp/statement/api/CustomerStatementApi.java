package com.leo.erp.statement.api;

import java.math.BigDecimal;
import java.util.Optional;

public interface CustomerStatementApi {

    Optional<Snapshot> findActiveById(Long statementId);

    Snapshot requireActiveById(Long statementId);

    /**
     * 收款核销可分配候选：要求未删除且为蓝字对账单；红字为退货冲销，不参与收款核销。
     */
    Snapshot requireActiveAllocatableById(Long statementId);

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
            String status,
            String direction
    ) {
    }
}
