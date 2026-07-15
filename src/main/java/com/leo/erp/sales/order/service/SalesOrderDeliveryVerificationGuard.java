package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class SalesOrderDeliveryVerificationGuard {

    private final CustomerStatementRepository customerStatementRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final SourceAllocationLockService sourceAllocationLockService;

    public SalesOrderDeliveryVerificationGuard(CustomerStatementRepository customerStatementRepository,
                                               ReceiptAllocationRepository receiptAllocationRepository,
                                               SourceAllocationLockService sourceAllocationLockService) {
        this.customerStatementRepository = customerStatementRepository;
        this.receiptAllocationRepository = receiptAllocationRepository;
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
        if (!customerStatementRepository.findSourceSalesOrderItemIds(stableItemIds).isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "销售订单已存在客户对账单，不能" + action + "，请先删除相关客户对账单"
            );
        }
        if (!receiptAllocationRepository.findReceivedSourceSalesOrderItemIds(
                stableItemIds,
                StatusConstants.AUDITED
        ).isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "销售订单已发生收款，不能" + action + "，请先删除相关收款单"
            );
        }
    }
}
