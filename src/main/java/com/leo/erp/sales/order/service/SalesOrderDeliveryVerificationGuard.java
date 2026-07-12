package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class SalesOrderDeliveryVerificationGuard {

    private final InvoiceIssueRepository invoiceIssueRepository;
    private final CustomerStatementRepository customerStatementRepository;
    private final SourceAllocationLockService sourceAllocationLockService;

    public SalesOrderDeliveryVerificationGuard(InvoiceIssueRepository invoiceIssueRepository,
                                               CustomerStatementRepository customerStatementRepository,
                                               SourceAllocationLockService sourceAllocationLockService) {
        this.invoiceIssueRepository = invoiceIssueRepository;
        this.customerStatementRepository = customerStatementRepository;
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
        if (!invoiceIssueRepository.summarizeAllocatedBySourceSalesOrderItemIds(stableItemIds, null).isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "销售订单已存在开票记录（含草稿或已开票），不能" + action + "，请先删除或反审核相关开票单"
            );
        }
        if (!customerStatementRepository.findSourceSalesOrderItemIds(stableItemIds).isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "销售订单已存在客户对账单，不能" + action + "，请先删除相关客户对账单"
            );
        }
    }
}
