package com.leo.erp.finance.payment.service;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.statement.api.StatementSettlementSyncCommand;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PaymentSettlementSyncService {

    private final StatementSettlementSyncCommand statementSettlementSyncCommand;

    public PaymentSettlementSyncService(StatementSettlementSyncCommand statementSettlementSyncCommand) {
        this.statementSettlementSyncCommand = statementSettlementSyncCommand;
    }

    void captureOriginalAllocationState(Payment entity) {
        entity.setOriginalBusinessType(entity.getCounterpartyType());
        entity.setOriginalAllocationStatementIds(collectStatementIds(entity.getCounterpartyType(), entity.getItems()));
    }

    Long resolveLegacySourceStatementId(Payment entity) {
        return entity.getItems().size() == 1
                ? statementId(entity.getCounterpartyType(), entity.getItems().get(0))
                : null;
    }

    void syncLinkedStatements(Payment entity) {
        Set<StatementLink> links = new LinkedHashSet<>();
        if (entity.getOriginalBusinessType() != null) {
            entity.getOriginalAllocationStatementIds()
                    .forEach(statementId -> links.add(new StatementLink(entity.getOriginalBusinessType(), statementId)));
        }
        entity.getItems()
                .forEach(item -> links.add(new StatementLink(
                        entity.getCounterpartyType(),
                        statementId(entity.getCounterpartyType(), item)
                )));
        for (StatementLink link : links) {
            if (link.statementId() == null
                    || !PaymentAllocationService.FREIGHT_PAYMENT_TYPE.equals(link.businessType())) {
                continue;
            }
            statementSettlementSyncCommand.syncFreightStatement(link.statementId());
        }
    }

    private Set<Long> collectStatementIds(String businessType, List<PaymentAllocation> items) {
        Set<Long> statementIds = new LinkedHashSet<>();
        if (!PaymentAllocationService.FREIGHT_PAYMENT_TYPE.equals(businessType)) {
            return statementIds;
        }
        for (PaymentAllocation item : items) {
            Long statementId = statementId(businessType, item);
            if (statementId != null) {
                statementIds.add(statementId);
            }
        }
        return statementIds;
    }

    private Long statementId(String businessType, PaymentAllocation item) {
        Long typedId = item.getSourceFreightStatementId();
        return typedId == null ? item.getSourceStatementId() : typedId;
    }

    private record StatementLink(String businessType, Long statementId) {
    }
}
