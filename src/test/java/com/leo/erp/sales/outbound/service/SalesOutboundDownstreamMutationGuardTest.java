package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
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
                StatusConstants.RECEIVED
        )).thenReturn(List.of(101L));

        assertThatThrownBy(() -> fixture.guard.assertReverseAuditAllowed(fixture.outbound))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收款")
                .hasMessageContaining("不能反审核");
    }

    @Test
    void shouldRejectReverseAuditWhenFreightBillExists() {
        Fixture fixture = new Fixture();
        when(fixture.freightBillRepository.findAllBySourceNosExcludingCurrentBill(
                List.of("SOO-001"),
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
        verify(fixture.customerStatementRepository).findSourceSalesOrderItemIds(List.of(101L));
        verify(fixture.receiptAllocationRepository).findReceivedSourceSalesOrderItemIds(
                List.of(101L),
                StatusConstants.RECEIVED
        );
        verify(fixture.freightBillRepository).findAllBySourceNosExcludingCurrentBill(
                List.of("SOO-001"),
                null
        );
    }

    private static final class Fixture {
        private final CustomerStatementRepository customerStatementRepository =
                mock(CustomerStatementRepository.class);
        private final ReceiptAllocationRepository receiptAllocationRepository =
                mock(ReceiptAllocationRepository.class);
        private final FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        private final SalesOutboundDownstreamMutationGuard guard =
                new SalesOutboundDownstreamMutationGuard(
                        customerStatementRepository,
                        receiptAllocationRepository,
                        freightBillRepository
                );
        private final SalesOutbound outbound = outbound();

        private Fixture() {
            when(customerStatementRepository.findSourceSalesOrderItemIds(List.of(101L)))
                    .thenReturn(List.of());
            when(receiptAllocationRepository.findReceivedSourceSalesOrderItemIds(
                    List.of(101L),
                    StatusConstants.RECEIVED
            )).thenReturn(List.of());
            when(freightBillRepository.findAllBySourceNosExcludingCurrentBill(
                    List.of("SOO-001"),
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
