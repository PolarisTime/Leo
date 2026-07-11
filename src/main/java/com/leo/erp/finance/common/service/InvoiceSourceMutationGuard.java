package com.leo.erp.finance.common.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class InvoiceSourceMutationGuard {

    private final InvoiceReceiptRepository invoiceReceiptRepository;
    private final InvoiceIssueRepository invoiceIssueRepository;
    private final SourceAllocationLockService sourceAllocationLockService;

    public InvoiceSourceMutationGuard(InvoiceReceiptRepository invoiceReceiptRepository,
                                      InvoiceIssueRepository invoiceIssueRepository,
                                      SourceAllocationLockService sourceAllocationLockService) {
        this.invoiceReceiptRepository = invoiceReceiptRepository;
        this.invoiceIssueRepository = invoiceIssueRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    public void assertPurchaseOrderMutable(PurchaseOrder order, String action) {
        List<Long> itemIds = order.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (itemIds.isEmpty()) {
            return;
        }
        sourceAllocationLockService.lockTradeItemSources(itemIds, List.of(), List.of());
        if (!invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(
                itemIds,
                null
        ).isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单已存在收票记录（含草稿或已收票），不能" + action + "，请先删除或反审核相关收票单"
            );
        }
    }

    public void assertSalesOrderMutable(SalesOrder order, String action) {
        List<Long> itemIds = order.getItems().stream()
                .map(SalesOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (itemIds.isEmpty()) {
            return;
        }
        sourceAllocationLockService.lockTradeItemSources(List.of(), List.of(), itemIds);
        if (!invoiceIssueRepository.summarizeAllocatedBySourceSalesOrderItemIds(
                itemIds,
                null
        ).isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "销售订单已存在开票记录（含草稿或已开票），不能" + action + "，请先删除或反审核相关开票单"
            );
        }
    }
}
