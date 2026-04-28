package com.leo.erp.statement.service;

import com.leo.erp.finance.payment.service.PaymentSettledEvent;
import com.leo.erp.finance.receipt.service.ReceiptSettledEvent;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StatementSettlementEventListener {

    private static final Logger log = LoggerFactory.getLogger(StatementSettlementEventListener.class);
    private static final String SUPPLIER_PAYMENT_TYPE = "供应商";
    private static final String FREIGHT_PAYMENT_TYPE = "物流商";

    private final StatementSettlementSyncService syncService;
    private final SupplierStatementRepository supplierStatementRepository;
    private final FreightStatementRepository freightStatementRepository;
    private final CustomerStatementRepository customerStatementRepository;

    public StatementSettlementEventListener(StatementSettlementSyncService syncService,
                                             SupplierStatementRepository supplierStatementRepository,
                                             FreightStatementRepository freightStatementRepository,
                                             CustomerStatementRepository customerStatementRepository) {
        this.syncService = syncService;
        this.supplierStatementRepository = supplierStatementRepository;
        this.freightStatementRepository = freightStatementRepository;
        this.customerStatementRepository = customerStatementRepository;
    }

    @EventListener
    public void onPaymentSettled(PaymentSettledEvent event) {
        if (event.statementId() == null) {
            return;
        }
        if (SUPPLIER_PAYMENT_TYPE.equals(event.businessType())) {
            supplierStatementRepository.findByIdAndDeletedFlagFalse(event.statementId())
                    .ifPresent(syncService::syncSupplierStatement);
        } else if (FREIGHT_PAYMENT_TYPE.equals(event.businessType())) {
            freightStatementRepository.findByIdAndDeletedFlagFalse(event.statementId())
                    .ifPresent(syncService::syncFreightStatement);
        }
    }

    @EventListener
    public void onReceiptSettled(ReceiptSettledEvent event) {
        if (event.statementId() == null) {
            return;
        }
        customerStatementRepository.findByIdAndDeletedFlagFalse(event.statementId())
                .ifPresent(syncService::syncCustomerStatement);
    }
}
