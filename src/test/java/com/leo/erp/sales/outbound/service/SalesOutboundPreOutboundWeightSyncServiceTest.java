package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.service.FreightBillPendingWeightSyncService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOutboundPreOutboundWeightSyncServiceTest {

    @Test
    void shouldRefreshOnlyPreOutboundItemsAndCascadeToPendingFreightBills() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        FreightBillPendingWeightSyncService freightBillSyncService = mock(FreightBillPendingWeightSyncService.class);
        SalesOutboundPreOutboundWeightSyncService service =
                new SalesOutboundPreOutboundWeightSyncService(repository, freightBillSyncService);

        SalesOutbound outbound = outbound(StatusConstants.PRE_OUTBOUND);
        SalesOutboundItem matched = outboundItem(outbound, 501L, 301L, "1.000", "3000.00");
        SalesOutboundItem unchanged = outboundItem(outbound, 502L, 302L, "3.000", "4000.00");
        outbound.getItems().add(matched);
        outbound.getItems().add(unchanged);

        when(repository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                StatusConstants.PRE_OUTBOUND,
                List.of(301L)
        )).thenReturn(List.of(outbound));

        service.syncBySalesOrderItemWeights(Map.of(301L, new BigDecimal("2.500")));

        assertThat(matched.getWeightTon()).isEqualByComparingTo("2.50000000");
        assertThat(matched.getAmount()).isEqualByComparingTo("7500.00");
        assertThat(unchanged.getWeightTon()).isEqualByComparingTo("3.000");
        assertThat(outbound.getTotalWeight()).isEqualByComparingTo("5.50000000");
        assertThat(outbound.getTotalAmount()).isEqualByComparingTo("19500.00");
        verify(repository).saveAll(List.of(outbound));
        verify(freightBillSyncService).syncBySalesOutboundItemWeights(Map.of(501L, new BigDecimal("2.50000000")));
    }

    private SalesOutbound outbound(String status) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setStatus(status);
        outbound.setTotalWeight(BigDecimal.ZERO);
        outbound.setTotalAmount(BigDecimal.ZERO);
        return outbound;
    }

    private SalesOutboundItem outboundItem(SalesOutbound outbound,
                                           Long id,
                                           Long sourceSalesOrderItemId,
                                           String weightTon,
                                           String unitPrice) {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(id);
        item.setSalesOutbound(outbound);
        item.setSourceSalesOrderItemId(sourceSalesOrderItemId);
        item.setWeightTon(new BigDecimal(weightTon));
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setAmount(BigDecimal.ZERO);
        return item;
    }
}
