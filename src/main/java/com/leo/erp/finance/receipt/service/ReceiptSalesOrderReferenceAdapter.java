package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.sales.api.SalesOrderReceiptReferenceQuery;
import com.leo.erp.statement.api.CustomerStatementSourceReferenceQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class ReceiptSalesOrderReferenceAdapter implements SalesOrderReceiptReferenceQuery {

    private final CustomerStatementSourceReferenceQuery statementReferenceQuery;
    private final ReceiptAllocationRepository receiptAllocationRepository;

    public ReceiptSalesOrderReferenceAdapter(
            CustomerStatementSourceReferenceQuery statementReferenceQuery,
            ReceiptAllocationRepository receiptAllocationRepository
    ) {
        this.statementReferenceQuery = statementReferenceQuery;
        this.receiptAllocationRepository = receiptAllocationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAuditedReceiptReferences(Collection<Long> salesOrderItemIds) {
        List<Long> statementIds = statementReferenceQuery
                .findActiveStatementIdsBySalesOrderItemIds(salesOrderItemIds);
        if (statementIds.isEmpty()) {
            return false;
        }
        return receiptAllocationRepository.countBySourceStatementIdsAndReceiptStatus(
                statementIds,
                StatusConstants.AUDITED
        ) > 0;
    }
}
