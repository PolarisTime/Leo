package com.leo.erp.statement.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import org.springframework.stereotype.Service;

@Service
public class StatementSettlementMutationGuard {

    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;

    public StatementSettlementMutationGuard(PaymentAllocationRepository paymentAllocationRepository,
                                            ReceiptAllocationRepository receiptAllocationRepository) {
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.receiptAllocationRepository = receiptAllocationRepository;
    }

    public void assertNoSettledAllocations(StatementType statementType, Long statementId, String action) {
        if (statementType == null || statementId == null) {
            return;
        }
        long allocationCount = switch (statementType) {
            case SUPPLIER, FREIGHT -> paymentAllocationRepository
                    .countSettledAllocationsByStatementIdAndBusinessTypeAndStatus(
                            statementId,
                            statementType.businessType(),
                            StatementSettlementSyncService.PAYMENT_STATUS_SETTLED
                    );
            case CUSTOMER -> receiptAllocationRepository.countSettledAllocationsByStatementIdAndStatus(
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

    public void assertFinancialLinkageMutationAllowed(StatementType statementType,
                                                      Long statementId,
                                                      boolean financialLinkageChanged) {
        if (!financialLinkageChanged) {
            return;
        }
        assertNoSettledAllocations(statementType, statementId, "修改往来单位或来源");
    }

    public enum StatementType {
        SUPPLIER("供应商对账单", "已付款", "供应商"),
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
