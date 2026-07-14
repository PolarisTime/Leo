package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOutboundOrderQueryServiceTest {

    @Test
    void shouldMapAuditedOutboundsSelectedBySourceItemIds() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutbound outbound = outbound();
        when(repository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                StatusConstants.AUDITED,
                List.of(101L)
        )).thenReturn(List.of(outbound));

        SalesOutboundOrderQueryService service = new SalesOutboundOrderQueryService(repository);

        var records = service.findAuditedOutboundsBySourceSalesOrderItemIds(List.of(101L));

        assertThat(records).hasSize(1);
        assertThat(records.get(0).salesOrderNo()).isEqualTo("SO-001");
        assertThat(records.get(0).status()).isEqualTo(StatusConstants.AUDITED);
        assertThat(records.get(0).items()).hasSize(1);
        assertThat(records.get(0).items().get(0).sourceSalesOrderItemId()).isEqualTo(101L);
        assertThat(records.get(0).items().get(0).quantity()).isEqualTo(3);
        assertThat(records.get(0).items().get(0).weightTon()).isEqualByComparingTo("4.500");
    }

    private SalesOutbound outbound() {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setOutboundNo("CK-001");
        outbound.setSalesOrderNo("SO-001");
        outbound.setStatus(StatusConstants.AUDITED);

        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(11L);
        item.setSalesOutbound(outbound);
        item.setLineNo(1);
        item.setSourceSalesOrderItemId(101L);
        item.setQuantity(3);
        item.setWeightTon(new BigDecimal("4.500"));
        outbound.setItems(List.of(item));
        return outbound;
    }
}
