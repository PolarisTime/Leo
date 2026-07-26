package com.leo.erp.finance.common.service;

import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.statement.api.StatementSettlementAllocationPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class StatementSettlementAllocationAdapter implements StatementSettlementAllocationPort {

    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;

    public StatementSettlementAllocationAdapter(PaymentAllocationRepository paymentAllocationRepository,
                                                ReceiptAllocationRepository receiptAllocationRepository) {
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.receiptAllocationRepository = receiptAllocationRepository;
    }

    @Override
    public BigDecimal sumPaymentAmount(Long statementId, String businessType, String status) {
        return paymentAllocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus(
                statementId,
                businessType,
                status
        );
    }

    @Override
    public BigDecimal sumReceiptAmount(Long statementId, String status) {
        return receiptAllocationRepository.sumAllocatedAmountBySourceStatementIdAndReceiptStatus(statementId, status);
    }

    @Override
    public long countPaymentAllocations(Long statementId, String businessType, String status) {
        return paymentAllocationRepository.countSettledAllocationsByStatementIdAndBusinessTypeAndStatus(
                statementId,
                businessType,
                status
        );
    }

    @Override
    public long countReceiptAllocations(Long statementId, String status) {
        return receiptAllocationRepository.countSettledAllocationsByStatementIdAndStatus(statementId, status);
    }
}
