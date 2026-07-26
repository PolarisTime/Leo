package com.leo.erp.statement.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.statement.api.StatementSettlementAllocationPort;
import com.leo.erp.statement.api.StatementSettlementSyncCommand;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class StatementSettlementSyncService implements StatementSettlementSyncCommand {

    public static final String PAYMENT_STATUS_SETTLED = StatusConstants.AUDITED;
    public static final String RECEIPT_STATUS_SETTLED = StatusConstants.AUDITED;
    private static final String FREIGHT_PAYMENT_TYPE = "物流商";

    private final CustomerStatementRepository customerStatementRepository;
    private final FreightStatementRepository freightStatementRepository;
    private final StatementSettlementAllocationPort settlementAllocationPort;

    public StatementSettlementSyncService(CustomerStatementRepository customerStatementRepository,
                                          FreightStatementRepository freightStatementRepository,
                                          StatementSettlementAllocationPort settlementAllocationPort) {
        this.customerStatementRepository = customerStatementRepository;
        this.freightStatementRepository = freightStatementRepository;
        this.settlementAllocationPort = settlementAllocationPort;
    }

    @Override
    @Transactional
    public void syncCustomerStatement(Long statementId) {
        if (statementId == null) {
            return;
        }
        customerStatementRepository.findByIdAndDeletedFlagFalseForSettlementUpdate(statementId)
                .ifPresent(this::syncCustomerStatement);
    }

    @Override
    @Transactional
    public void syncFreightStatement(Long statementId) {
        if (statementId == null) {
            return;
        }
        freightStatementRepository.findByIdAndDeletedFlagFalseForSettlementUpdate(statementId)
                .ifPresent(this::syncFreightStatement);
    }

    @Transactional
    public CustomerStatement syncCustomerStatement(CustomerStatement statement) {
        BigDecimal receiptAmount = resolveCustomerReceiptAmount(statement.getId());
        statement.setReceiptAmount(receiptAmount);
        statement.setClosingAmount(statement.getSalesAmount().subtract(receiptAmount).max(BigDecimal.ZERO));
        return customerStatementRepository.save(statement);
    }

    @Transactional
    public FreightStatement syncFreightStatement(FreightStatement statement) {
        BigDecimal paidAmount = settlementAllocationPort.sumPaymentAmount(
                statement.getId(),
                FREIGHT_PAYMENT_TYPE,
                PAYMENT_STATUS_SETTLED
        );
        statement.setPaidAmount(paidAmount);
        statement.setUnpaidAmount(statement.getTotalFreight().subtract(paidAmount).max(BigDecimal.ZERO));
        return freightStatementRepository.save(statement);
    }

    public BigDecimal resolveCustomerReceiptAmount(Long statementId) {
        if (statementId == null) {
            return TradeItemCalculator.scaleAmount(BigDecimal.ZERO);
        }
        return TradeItemCalculator.scaleAmount(TradeItemCalculator.safeBigDecimal(
                settlementAllocationPort.sumReceiptAmount(
                        statementId,
                        RECEIPT_STATUS_SETTLED
                )
        ));
    }
}
