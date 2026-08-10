package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.service.SalesOrderOutboundQueryService.OutboundItemRecord;
import com.leo.erp.sales.order.service.SalesOrderOutboundQueryService.OutboundRecord;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOutboundOrderQueryService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOutboundOrderQueryServiceTest {

    @Mock
    private SalesOutboundRepository salesOutboundRepository;

    @InjectMocks
    private SalesOutboundOrderQueryService service;

    private SalesOutbound outbound() {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setSalesOrderNo("SO001");
        outbound.setStatus(StatusConstants.AUDITED);
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(11L);
        item.setQuantity(10);
        item.setWeightTon(new BigDecimal("12.500"));
        outbound.setItems(List.of(item));
        return outbound;
    }

    @Test
    void shouldMapAuditedOutboundsAndItems() {
        when(salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                StatusConstants.AUDITED, List.of(11L))).thenReturn(List.of(outbound()));

        List<OutboundRecord> result = service.findAuditedOutboundsBySourceSalesOrderItemIds(List.of(11L));

        assertThat(result).hasSize(1);
        OutboundRecord record = result.get(0);
        assertThat(record.salesOrderNo()).isEqualTo("SO001");
        assertThat(record.status()).isEqualTo(StatusConstants.AUDITED);
        OutboundItemRecord item = record.items().get(0);
        assertThat(item.sourceSalesOrderItemId()).isEqualTo(11L);
        assertThat(item.quantity()).isEqualTo(10);
        assertThat(item.weightTon()).isEqualByComparingTo("12.500");
    }

    @Test
    void shouldReturnEmptyWhenRepositoryReturnsNone() {
        when(salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                any(), any())).thenReturn(List.of());

        assertThat(service.findAuditedOutboundsBySourceSalesOrderItemIds(List.of(999L))).isEmpty();
    }

    @Test
    void shouldDelegateNullInputToRepositoryWithoutCrash() {
        when(salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                any(), any())).thenReturn(List.of());

        assertThat(service.findAuditedOutboundsBySourceSalesOrderItemIds(null)).isEmpty();
        verify(salesOutboundRepository).findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                StatusConstants.AUDITED, null);
    }
}
