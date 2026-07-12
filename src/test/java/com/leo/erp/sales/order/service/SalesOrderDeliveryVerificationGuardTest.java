package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderDeliveryVerificationGuardTest {

    @Test
    void shouldRejectVerificationWhenCustomerStatementOccupiesOrderItem() {
        InvoiceIssueRepository invoiceRepository = mock(InvoiceIssueRepository.class);
        CustomerStatementRepository statementRepository = mock(CustomerStatementRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SalesOrderDeliveryVerificationGuard guard =
                new SalesOrderDeliveryVerificationGuard(invoiceRepository, statementRepository, lockService);
        SalesOrder order = orderWithItem(11L);
        when(statementRepository.findSourceSalesOrderItemIds(List.of(11L))).thenReturn(List.of(11L));

        assertThatThrownBy(() -> guard.assertMutable(order, "重新核定"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单")
                .hasMessageContaining("不能重新核定");
        verify(lockService).lockTradeItemSources(List.of(), List.of(), List.of(11L));
    }

    @Test
    void shouldRejectItemMutationWhenInvoiceOccupiesOrderItem() {
        InvoiceIssueRepository invoiceRepository = mock(InvoiceIssueRepository.class);
        CustomerStatementRepository statementRepository = mock(CustomerStatementRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SalesOrderDeliveryVerificationGuard guard =
                new SalesOrderDeliveryVerificationGuard(invoiceRepository, statementRepository, lockService);
        InvoiceIssueRepository.SourceAllocationSummary allocation =
                mock(InvoiceIssueRepository.SourceAllocationSummary.class);
        when(invoiceRepository.summarizeAllocatedBySourceSalesOrderItemIds(List.of(11L), null))
                .thenReturn(List.of(allocation));

        assertThatThrownBy(() -> guard.assertMutableByItemIds(List.of(11L), "反审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开票")
                .hasMessageContaining("不能反审核");
        verify(lockService).lockTradeItemSources(List.of(), List.of(), List.of(11L));
    }

    @Test
    void shouldAllowVerificationWhenNoFinancialDocumentOccupiesOrderItem() {
        InvoiceIssueRepository invoiceRepository = mock(InvoiceIssueRepository.class);
        CustomerStatementRepository statementRepository = mock(CustomerStatementRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SalesOrderDeliveryVerificationGuard guard =
                new SalesOrderDeliveryVerificationGuard(invoiceRepository, statementRepository, lockService);
        SalesOrder order = orderWithItem(11L);
        when(statementRepository.findSourceSalesOrderItemIds(List.of(11L))).thenReturn(List.of());

        guard.assertMutable(order, "重新核定");

        verify(lockService).lockTradeItemSources(List.of(), List.of(), List.of(11L));
        verify(statementRepository).findSourceSalesOrderItemIds(List.of(11L));
    }

    private SalesOrder orderWithItem(Long itemId) {
        SalesOrder order = new SalesOrder();
        SalesOrderItem item = new SalesOrderItem();
        item.setId(itemId);
        item.setSalesOrder(order);
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }
}
