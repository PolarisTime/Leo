package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreightBillPendingWeightSyncServiceTest {

    @Test
    void shouldRefreshUnauditedFreightBillItemsFromSourceOutboundWeights() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        FreightBillPendingWeightSyncService service = new FreightBillPendingWeightSyncService(repository);

        FreightBill bill = freightBill(StatusConstants.UNAUDITED);
        FreightBillItem matched = freightBillItem(bill, 401L, "1.000");
        FreightBillItem unchanged = freightBillItem(bill, 402L, "3.000");
        bill.getItems().add(matched);
        bill.getItems().add(unchanged);

        when(repository.findAllByStatusAndSourceSalesOutboundItemIds(
                StatusConstants.UNAUDITED,
                List.of(401L)
        )).thenReturn(List.of(bill));

        service.syncBySalesOutboundItemWeights(Map.of(401L, new BigDecimal("2.500")));

        assertThat(matched.getWeightTon()).isEqualByComparingTo("2.50000000");
        assertThat(unchanged.getWeightTon()).isEqualByComparingTo("3.000");
        assertThat(bill.getTotalWeight()).isEqualByComparingTo("5.50000000");
        assertThat(bill.getTotalFreight()).isEqualByComparingTo("55.00");
        verify(repository).saveAll(List.of(bill));
    }

    private FreightBill freightBill(String status) {
        FreightBill bill = new FreightBill();
        bill.setId(1L);
        bill.setStatus(status);
        bill.setUnitPrice(new BigDecimal("10.00"));
        bill.setTotalWeight(BigDecimal.ZERO);
        bill.setTotalFreight(BigDecimal.ZERO);
        return bill;
    }

    private FreightBillItem freightBillItem(FreightBill bill, Long sourceSalesOutboundItemId, String weightTon) {
        FreightBillItem item = new FreightBillItem();
        item.setId(sourceSalesOutboundItemId + 1000L);
        item.setFreightBill(bill);
        item.setSourceSalesOutboundItemId(sourceSalesOutboundItemId);
        item.setWeightTon(new BigDecimal(weightTon));
        return item;
    }
}
