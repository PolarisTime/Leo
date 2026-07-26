package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.sales.api.SalesOrderReceiptReferenceQuery;
import com.leo.erp.sales.api.SalesOrderStatementReferenceQuery;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class SalesOrderDeliveryVerificationGuard {

    private final SalesOrderStatementReferenceQuery statementReferenceQuery;
    private final SalesOrderReceiptReferenceQuery receiptReferenceQuery;
    private final SourceAllocationLockService sourceAllocationLockService;

    public SalesOrderDeliveryVerificationGuard(SalesOrderStatementReferenceQuery statementReferenceQuery,
                                               SalesOrderReceiptReferenceQuery receiptReferenceQuery,
                                               SourceAllocationLockService sourceAllocationLockService) {
        this.statementReferenceQuery = statementReferenceQuery;
        this.receiptReferenceQuery = receiptReferenceQuery;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    public void assertMutable(SalesOrder order, String action) {
        List<Long> itemIds = order.getItems().stream()
                .map(SalesOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        assertMutableByItemIds(itemIds, action);
    }

    public void assertMutableByItemIds(List<Long> itemIds, String action) {
        List<Long> stableItemIds = itemIds == null ? List.of() : itemIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (stableItemIds.isEmpty()) {
            return;
        }
        sourceAllocationLockService.lockTradeItemSources(List.of(), List.of(), stableItemIds);
        if (statementReferenceQuery.hasActiveCustomerStatementReferences(stableItemIds)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "销售订单已存在客户对账单，不能" + action + "，请先删除相关客户对账单"
            );
        }
        if (receiptReferenceQuery.hasAuditedReceiptReferences(stableItemIds)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "销售订单已发生收款，不能" + action + "，请先删除相关收款单"
            );
        }
    }
}
