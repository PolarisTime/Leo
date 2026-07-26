package com.leo.erp.statement.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.statement.api.StatementSettlementAllocationPort;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.springframework.stereotype.Service;

@Service
public class StatementSettlementMutationGuard {

    private final StatementSettlementAllocationPort settlementAllocationPort;
    private final CustomerStatementRepository customerStatementRepository;
    private final FreightStatementRepository freightStatementRepository;

    public StatementSettlementMutationGuard(StatementSettlementAllocationPort settlementAllocationPort,
                                            CustomerStatementRepository customerStatementRepository,
                                            FreightStatementRepository freightStatementRepository) {
        this.settlementAllocationPort = settlementAllocationPort;
        this.customerStatementRepository = customerStatementRepository;
        this.freightStatementRepository = freightStatementRepository;
    }

    public void assertNoSettledAllocations(StatementType statementType, Long statementId, String action) {
        if (statementType == null || statementId == null) {
            return;
        }
        // 收付款审核以对账单行锁串行化；这里先锁同一行再计数，封死 check-then-act 窗口
        lockStatementRow(statementType, statementId);
        long allocationCount = switch (statementType) {
            case FREIGHT -> settlementAllocationPort.countPaymentAllocations(
                    statementId,
                    statementType.businessType(),
                    StatementSettlementSyncService.PAYMENT_STATUS_SETTLED
            );
            case CUSTOMER -> settlementAllocationPort.countReceiptAllocations(
                    statementId,
                    StatementSettlementSyncService.RECEIPT_STATUS_SETTLED
            );
        };
        if (allocationCount <= 0) {
            return;
        }
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                statementType.displayName()
                        + "已存在"
                        + statementType.settlementLabel()
                        + "核销，不能"
                        + action
                        + "，请先反核销对应收付款单"
        );
    }

    private void lockStatementRow(StatementType statementType, Long statementId) {
        switch (statementType) {
            case CUSTOMER -> customerStatementRepository
                    .findByIdAndDeletedFlagFalseForSettlementUpdate(statementId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "客户对账单不存在"));
            case FREIGHT -> freightStatementRepository
                    .findByIdAndDeletedFlagFalseForSettlementUpdate(statementId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "物流对账单不存在"));
        }
    }

    public void assertFinancialLinkageMutationAllowed(StatementType statementType,
                                                      Long statementId,
                                                      boolean financialLinkageChanged) {
        if (!financialLinkageChanged) {
            return;
        }
        assertNoSettledAllocations(statementType, statementId, "修改往来单位或来源");
    }

    public enum StatementType {
        CUSTOMER("客户对账单", "已收款", null),
        FREIGHT("物流对账单", "已付款", "物流商");

        private final String displayName;
        private final String settlementLabel;
        private final String businessType;

        StatementType(String displayName, String settlementLabel, String businessType) {
            this.displayName = displayName;
            this.settlementLabel = settlementLabel;
            this.businessType = businessType;
        }

        String displayName() {
            return displayName;
        }

        String settlementLabel() {
            return settlementLabel;
        }

        String businessType() {
            return businessType;
        }
    }
}
