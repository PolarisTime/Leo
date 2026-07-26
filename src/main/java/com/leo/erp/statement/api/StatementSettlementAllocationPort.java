package com.leo.erp.statement.api;

import java.math.BigDecimal;

public interface StatementSettlementAllocationPort {

    BigDecimal sumPaymentAmount(Long statementId, String businessType, String status);

    BigDecimal sumReceiptAmount(Long statementId, String status);

    long countPaymentAllocations(Long statementId, String businessType, String status);

    long countReceiptAllocations(Long statementId, String status);
}
