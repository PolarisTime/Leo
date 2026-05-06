package com.leo.erp.statement.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class StatementSettlementSyncService {

    public static final String PAYMENT_STATUS_SETTLED = StatusConstants.PAID;
    public static final String RECEIPT_STATUS_SETTLED = StatusConstants.RECEIVED;
    private static final String SUPPLIER_PAYMENT_TYPE = "供应商";
    private static final String FREIGHT_PAYMENT_TYPE = "物流商";

    private final SupplierStatementRepository supplierStatementRepository;
    private final CustomerStatementRepository customerStatementRepository;
    private final FreightStatementRepository freightStatementRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;

    public StatementSettlementSyncService(SupplierStatementRepository supplierStatementRepository,
                                          CustomerStatementRepository customerStatementRepository,
                                          FreightStatementRepository freightStatementRepository,
                                          PaymentAllocationRepository paymentAllocationRepository,
                                          ReceiptAllocationRepository receiptAllocationRepository) {
        this.supplierStatementRepository = supplierStatementRepository;
        this.customerStatementRepository = customerStatementRepository;
        this.freightStatementRepository = freightStatementRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.receiptAllocationRepository = receiptAllocationRepository;
    }

    @Transactional
    public SupplierStatement syncSupplierStatement(SupplierStatement statement) {
        BigDecimal paymentAmount = paymentAllocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus(
                statement.getId(),
                SUPPLIER_PAYMENT_TYPE,
                PAYMENT_STATUS_SETTLED
        );
        statement.setPaymentAmount(paymentAmount);
        statement.setClosingAmount(statement.getPurchaseAmount().subtract(paymentAmount).max(BigDecimal.ZERO));
        return supplierStatementRepository.save(statement);
    }

    @Transactional
    public CustomerStatement syncCustomerStatement(CustomerStatement statement) {
        BigDecimal receiptAmount = receiptAllocationRepository.sumAllocatedAmountBySourceStatementIdAndReceiptStatus(
                statement.getId(),
                RECEIPT_STATUS_SETTLED
        );
        statement.setReceiptAmount(receiptAmount);
        statement.setClosingAmount(statement.getSalesAmount().subtract(receiptAmount).max(BigDecimal.ZERO));
        return customerStatementRepository.save(statement);
    }

    @Transactional
    public FreightStatement syncFreightStatement(FreightStatement statement) {
        BigDecimal paidAmount = paymentAllocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus(
                statement.getId(),
                FREIGHT_PAYMENT_TYPE,
                PAYMENT_STATUS_SETTLED
        );
        statement.setPaidAmount(paidAmount);
        statement.setUnpaidAmount(statement.getTotalFreight().subtract(paidAmount).max(BigDecimal.ZERO));
        return freightStatementRepository.save(statement);
    }
}
