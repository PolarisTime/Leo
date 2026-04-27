package com.leo.erp.statement.service;

import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.receipt.repository.ReceiptRepository;
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

    public static final String PAYMENT_STATUS_SETTLED = "已付款";
    public static final String RECEIPT_STATUS_SETTLED = "已收款";

    private final SupplierStatementRepository supplierStatementRepository;
    private final CustomerStatementRepository customerStatementRepository;
    private final FreightStatementRepository freightStatementRepository;
    private final PaymentRepository paymentRepository;
    private final ReceiptRepository receiptRepository;

    public StatementSettlementSyncService(SupplierStatementRepository supplierStatementRepository,
                                          CustomerStatementRepository customerStatementRepository,
                                          FreightStatementRepository freightStatementRepository,
                                          PaymentRepository paymentRepository,
                                          ReceiptRepository receiptRepository) {
        this.supplierStatementRepository = supplierStatementRepository;
        this.customerStatementRepository = customerStatementRepository;
        this.freightStatementRepository = freightStatementRepository;
        this.paymentRepository = paymentRepository;
        this.receiptRepository = receiptRepository;
    }

    @Transactional
    public SupplierStatement syncSupplierStatement(SupplierStatement statement) {
        BigDecimal paymentAmount = paymentRepository.sumAmountBySourceStatementIdAndStatus(statement.getId(), PAYMENT_STATUS_SETTLED);
        statement.setPaymentAmount(paymentAmount);
        statement.setClosingAmount(statement.getPurchaseAmount().subtract(paymentAmount).max(BigDecimal.ZERO));
        return supplierStatementRepository.save(statement);
    }

    @Transactional
    public CustomerStatement syncCustomerStatement(CustomerStatement statement) {
        BigDecimal receiptAmount = receiptRepository.sumAmountBySourceStatementIdAndStatus(statement.getId(), RECEIPT_STATUS_SETTLED);
        statement.setReceiptAmount(receiptAmount);
        statement.setClosingAmount(statement.getSalesAmount().subtract(receiptAmount).max(BigDecimal.ZERO));
        return customerStatementRepository.save(statement);
    }

    @Transactional
    public FreightStatement syncFreightStatement(FreightStatement statement) {
        BigDecimal paidAmount = paymentRepository.sumAmountBySourceStatementIdAndStatus(statement.getId(), PAYMENT_STATUS_SETTLED);
        statement.setPaidAmount(paidAmount);
        statement.setUnpaidAmount(statement.getTotalFreight().subtract(paidAmount).max(BigDecimal.ZERO));
        return freightStatementRepository.save(statement);
    }
}
