package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderDeliveryVerificationGuard;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOutboundDownstreamMutationGuardTest {

    @Test
    void shouldRejectReverseAuditWhenInvoiceIssueExists() {
        Fixture fixture = new Fixture();
        InvoiceIssueRepository.SourceAllocationSummary allocation =
                mock(InvoiceIssueRepository.SourceAllocationSummary.class);
        when(fixture.invoiceIssueRepository.summarizeAllocatedBySourceSalesOrderItemIds(
                List.of(101L),
                null
        )).thenReturn(List.of(allocation));

        assertThatThrownBy(() -> fixture.guard.assertReverseAuditAllowed(fixture.outbound))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开票")
                .hasMessageContaining("不能反审核");
    }

    @Test
    void shouldRejectReverseAuditWhenCustomerStatementExists() {
        Fixture fixture = new Fixture();
        when(fixture.customerStatementRepository.findSourceSalesOrderItemIds(List.of(101L)))
                .thenReturn(List.of(101L));

        assertThatThrownBy(() -> fixture.guard.assertReverseAuditAllowed(fixture.outbound))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单")
                .hasMessageContaining("不能反审核");
    }

    @Test
    void shouldRejectReverseAuditWhenReceivedReceiptExists() {
        Fixture fixture = new Fixture();
        when(fixture.receiptAllocationRepository.findReceivedSourceSalesOrderItemIds(
                List.of(101L),
                StatusConstants.AUDITED
        )).thenReturn(List.of(101L));

        assertThatThrownBy(() -> fixture.guard.assertReverseAuditAllowed(fixture.outbound))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款")
                .hasMessageContaining("不能反审核");
    }

    @Test
    void shouldRejectReverseAuditWhenFreightBillExists() {
        Fixture fixture = new Fixture();
        when(fixture.freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(
                List.of(201L),
                null
        )).thenReturn(List.of(new FreightBill()));

        assertThatThrownBy(() -> fixture.guard.assertReverseAuditAllowed(fixture.outbound))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单")
                .hasMessageContaining("不能反审核");
    }

    @Test
    void shouldAllowReverseAuditWithoutDownstreamReferences() {
        Fixture fixture = new Fixture();

        assertThatCode(() -> fixture.guard.assertReverseAuditAllowed(fixture.outbound))
                .doesNotThrowAnyException();
        verify(fixture.invoiceIssueRepository).summarizeAllocatedBySourceSalesOrderItemIds(
                List.of(101L),
                null
        );
        verify(fixture.customerStatementRepository).findSourceSalesOrderItemIds(List.of(101L));
        verify(fixture.receiptAllocationRepository).findReceivedSourceSalesOrderItemIds(
                List.of(101L),
                StatusConstants.AUDITED
        );
        verify(fixture.freightBillRepository).findAllBySourceItemIdsExcludingCurrentBill(
                List.of(201L),
                null
        );
    }

    @Test
    void shouldRejectReverseAuditWhenSourceSalesOrderIsCompleted() {
        Fixture fixture = new Fixture();
        SalesOrder completedOrder = new SalesOrder();
        completedOrder.setStatus(StatusConstants.SALES_COMPLETED);
        when(fixture.salesOrderRepository.findAllWithItemsBySourceItemIds(List.of(101L)))
                .thenReturn(List.of(completedOrder));

        assertThatThrownBy(() -> fixture.guard.assertReverseAuditAllowed(fixture.outbound))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已完成销售")
                .hasMessageContaining("不能反审核销售出库");
    }

    @Test
    void shouldRejectDeleteWhenSourceSalesOrderIsCompleted() {
        Fixture fixture = new Fixture();
        SalesOrder completedOrder = new SalesOrder();
        completedOrder.setStatus(StatusConstants.SALES_COMPLETED);
        when(fixture.salesOrderRepository.findAllWithItemsBySourceItemIds(List.of(101L)))
                .thenReturn(List.of(completedOrder));

        assertThatThrownBy(() -> fixture.guard.assertDeleteAllowed(fixture.outbound))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已完成销售")
                .hasMessageContaining("不能删除销售出库");
    }

    private static final class Fixture {
        private final InvoiceIssueRepository invoiceIssueRepository = mock(InvoiceIssueRepository.class);
        private final CustomerStatementRepository customerStatementRepository =
                mock(CustomerStatementRepository.class);
        private final SourceAllocationLockService sourceAllocationLockService =
                mock(SourceAllocationLockService.class);
        private final SalesOrderDeliveryVerificationGuard deliveryVerificationGuard =
                new SalesOrderDeliveryVerificationGuard(
                        invoiceIssueRepository,
                        customerStatementRepository,
                        sourceAllocationLockService
                );
        private final ReceiptAllocationRepository receiptAllocationRepository =
                mock(ReceiptAllocationRepository.class);
        private final FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        private final SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        private final SalesOutboundDownstreamMutationGuard guard =
                new SalesOutboundDownstreamMutationGuard(
                        deliveryVerificationGuard,
                        receiptAllocationRepository,
                        freightBillRepository,
                        salesOrderRepository
                );
        private final SalesOutbound outbound = outbound();

        private Fixture() {
            when(customerStatementRepository.findSourceSalesOrderItemIds(List.of(101L)))
                    .thenReturn(List.of());
            when(receiptAllocationRepository.findReceivedSourceSalesOrderItemIds(
                    List.of(101L),
                    StatusConstants.AUDITED
            )).thenReturn(List.of());
            when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(
                    List.of(201L),
                    null
            )).thenReturn(List.of());
        }

        private static SalesOutbound outbound() {
            SalesOutbound outbound = new SalesOutbound();
            outbound.setId(1L);
            outbound.setOutboundNo("SOO-001");
            SalesOutboundItem item = new SalesOutboundItem();
            item.setId(201L);
            item.setSourceSalesOrderItemId(101L);
            item.setSalesOutbound(outbound);
            outbound.setItems(new ArrayList<>(List.of(item)));
            return outbound;
        }
    }
}
