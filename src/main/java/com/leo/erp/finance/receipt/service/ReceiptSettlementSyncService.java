package com.leo.erp.finance.receipt.service;

import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import com.leo.erp.statement.api.StatementSettlementSyncCommand;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReceiptSettlementSyncService {

    private final StatementSettlementSyncCommand statementSettlementSyncCommand;

    public ReceiptSettlementSyncService(StatementSettlementSyncCommand statementSettlementSyncCommand) {
        this.statementSettlementSyncCommand = statementSettlementSyncCommand;
    }

    void captureOriginalAllocationStatementIds(Receipt entity) {
        entity.setOriginalAllocationStatementIds(collectStatementIds(entity.getItems()));
    }

    Long resolveLegacySourceStatementId(Receipt entity) {
        return entity.getItems().size() == 1 ? statementId(entity.getItems().get(0)) : null;
    }

    void syncCustomerStatements(Receipt entity) {
        Set<Long> statementIds = new LinkedHashSet<>(entity.getOriginalAllocationStatementIds());
        statementIds.addAll(collectStatementIds(entity.getItems()));
        for (Long statementId : statementIds) {
            statementSettlementSyncCommand.syncCustomerStatement(statementId);
        }
    }

    private Set<Long> collectStatementIds(List<ReceiptAllocation> items) {
        Set<Long> statementIds = new LinkedHashSet<>();
        for (ReceiptAllocation item : items) {
            Long statementId = statementId(item);
            if (statementId != null) {
                statementIds.add(statementId);
            }
        }
        return statementIds;
    }

    private Long statementId(ReceiptAllocation item) {
        return item.getSourceCustomerStatementId() == null
                ? item.getSourceStatementId()
                : item.getSourceCustomerStatementId();
    }
}
