package com.leo.erp.finance.common.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoiceSourceMutationGuardTest {

    @Test
    void shouldLockAndRejectPurchaseOrderMutationWhenReceivedInvoiceExists() {
        InvoiceReceiptRepository receiptRepository = mock(InvoiceReceiptRepository.class);
        InvoiceIssueRepository issueRepository = mock(InvoiceIssueRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        when(receiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(List.of(11L), null))
                .thenReturn(List.of(mock(InvoiceReceiptRepository.SourceAllocationSummary.class)));
        InvoiceSourceMutationGuard guard = new InvoiceSourceMutationGuard(
                receiptRepository,
                issueRepository,
                lockService
        );

        assertThatThrownBy(() -> guard.assertPurchaseOrderMutable(purchaseOrder(11L), "反审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已收票")
                .hasMessageContaining("不能反审核");

        var order = inOrder(lockService, receiptRepository);
        order.verify(lockService).lockTradeItemSources(List.of(11L), List.of(), List.of());
        order.verify(receiptRepository).summarizeAllocatedBySourcePurchaseOrderItemIds(List.of(11L), null);
    }

    @Test
    void shouldLockAndRejectSalesOrderMutationWhenIssuedInvoiceExists() {
        InvoiceReceiptRepository receiptRepository = mock(InvoiceReceiptRepository.class);
        InvoiceIssueRepository issueRepository = mock(InvoiceIssueRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        when(issueRepository.summarizeAllocatedBySourceSalesOrderItemIds(List.of(21L), null))
                .thenReturn(List.of(mock(InvoiceIssueRepository.SourceAllocationSummary.class)));
        InvoiceSourceMutationGuard guard = new InvoiceSourceMutationGuard(
                receiptRepository,
                issueRepository,
                lockService
        );

        assertThatThrownBy(() -> guard.assertSalesOrderMutable(salesOrder(21L), "删除"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已开票")
                .hasMessageContaining("不能删除");

        var order = inOrder(lockService, issueRepository);
        order.verify(lockService).lockTradeItemSources(List.of(), List.of(), List.of(21L));
        order.verify(issueRepository).summarizeAllocatedBySourceSalesOrderItemIds(List.of(21L), null);
    }

    @Test
    void shouldAllowMutationWhenNoEffectiveInvoiceExists() {
        InvoiceSourceMutationGuard guard = new InvoiceSourceMutationGuard(
                mock(InvoiceReceiptRepository.class),
                mock(InvoiceIssueRepository.class),
                mock(SourceAllocationLockService.class)
        );

        assertThatCode(() -> guard.assertPurchaseOrderMutable(purchaseOrder(11L), "修改"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectPurchaseOrderMutationWhenDraftInvoiceExists() {
        InvoiceReceiptRepository receiptRepository = mock(InvoiceReceiptRepository.class);
        InvoiceIssueRepository issueRepository = mock(InvoiceIssueRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        when(receiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(List.of(11L), null))
                .thenReturn(List.of(mock(InvoiceReceiptRepository.SourceAllocationSummary.class)));
        InvoiceSourceMutationGuard guard = new InvoiceSourceMutationGuard(
                receiptRepository,
                issueRepository,
                lockService
        );

        assertThatThrownBy(() -> guard.assertPurchaseOrderMutable(purchaseOrder(11L), "修改"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收票记录")
                .hasMessageContaining("不能修改");
    }

    @Test
    void shouldRejectSalesOrderMutationWhenDraftInvoiceExists() {
        InvoiceReceiptRepository receiptRepository = mock(InvoiceReceiptRepository.class);
        InvoiceIssueRepository issueRepository = mock(InvoiceIssueRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        when(issueRepository.summarizeAllocatedBySourceSalesOrderItemIds(List.of(21L), null))
                .thenReturn(List.of(mock(InvoiceIssueRepository.SourceAllocationSummary.class)));
        InvoiceSourceMutationGuard guard = new InvoiceSourceMutationGuard(
                receiptRepository,
                issueRepository,
                lockService
        );

        assertThatThrownBy(() -> guard.assertSalesOrderMutable(salesOrder(21L), "反审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开票记录")
                .hasMessageContaining("不能反审核");
    }

    private PurchaseOrder purchaseOrder(Long itemId) {
        PurchaseOrder order = new PurchaseOrder();
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(itemId);
        item.setPurchaseOrder(order);
        order.setItems(new java.util.ArrayList<>(List.of(item)));
        return order;
    }

    private SalesOrder salesOrder(Long itemId) {
        SalesOrder order = new SalesOrder();
        SalesOrderItem item = new SalesOrderItem();
        item.setId(itemId);
        item.setSalesOrder(order);
        order.setItems(new java.util.ArrayList<>(List.of(item)));
        return order;
    }
}
