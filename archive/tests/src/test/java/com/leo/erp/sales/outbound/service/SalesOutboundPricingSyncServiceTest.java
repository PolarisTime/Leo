package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOutboundPricingSyncServiceTest {

    @Test
    void shouldSyncAuditedOutboundPricingBySalesOrderItemId() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutbound outbound = outbound(101L, new BigDecimal("4.500"));
        when(repository.findAllByStatusesAndSourceSalesOrderItemIds(
                eq(List.of(StatusConstants.DRAFT, StatusConstants.AUDITED)),
                eq(List.of(101L))
        )).thenReturn(List.of(outbound));

        SalesOutboundPricingSyncService service = new SalesOutboundPricingSyncService(repository);

        service.syncAuditedOutboundPricing(List.of(101L), Map.of(101L, new BigDecimal("3888.00")));

        assertThat(outbound.getItems().get(0).getUnitPrice()).isEqualByComparingTo("3888.00");
        assertThat(outbound.getItems().get(0).getAmount()).isEqualByComparingTo("17496.00");
        assertThat(outbound.getTotalAmount()).isEqualByComparingTo("17496.00");
        verify(repository).saveAll(List.of(outbound));
    }

    @Test
    void shouldSkipRepositoryWhenNoSourceItemIds() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundPricingSyncService service = new SalesOutboundPricingSyncService(repository);

        service.syncAuditedOutboundPricing(List.of(), Map.of());

        verify(repository, never()).findAllByStatusesAndSourceSalesOrderItemIds(
                eq(List.of(StatusConstants.DRAFT, StatusConstants.AUDITED)),
                eq(List.of())
        );
    }

    @Test
    void shouldSkipRepositoryWhenNullInputsOrNoUnitPrices() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundPricingSyncService service = new SalesOutboundPricingSyncService(repository);

        service.syncAuditedOutboundPricing(null, Map.of(101L, BigDecimal.ONE));
        service.syncAuditedOutboundPricing(List.of(101L), null);
        service.syncAuditedOutboundPricing(List.of(101L), Map.of());

        verify(repository, never()).findAllByStatusesAndSourceSalesOrderItemIds(
                eq(List.of(StatusConstants.DRAFT, StatusConstants.AUDITED)),
                eq(List.of(101L))
        );
    }

    @Test
    void shouldReturnWhenNoAuditedOutboundsFound() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        when(repository.findAllByStatusesAndSourceSalesOrderItemIds(
                eq(List.of(StatusConstants.DRAFT, StatusConstants.AUDITED)),
                eq(List.of(101L))
        )).thenReturn(List.of());
        SalesOutboundPricingSyncService service = new SalesOutboundPricingSyncService(repository);

        service.syncAuditedOutboundPricing(List.of(101L), Map.of(101L, new BigDecimal("3888.00")));

        verify(repository, never()).saveAll(any());
    }

    @Test
    void shouldKeepExistingItemPricingWhenNoUnitPriceMappingExists() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutbound outbound = outbound(202L, new BigDecimal("1.500"));
        outbound.getItems().get(0).setUnitPrice(new BigDecimal("1000.00"));
        outbound.getItems().get(0).setAmount(new BigDecimal("1500.00"));
        when(repository.findAllByStatusesAndSourceSalesOrderItemIds(
                eq(List.of(StatusConstants.DRAFT, StatusConstants.AUDITED)),
                eq(List.of(202L))
        )).thenReturn(List.of(outbound));
        SalesOutboundPricingSyncService service = new SalesOutboundPricingSyncService(repository);

        service.syncAuditedOutboundPricing(List.of(202L), Map.of(101L, new BigDecimal("3888.00")));

        assertThat(outbound.getItems().get(0).getUnitPrice()).isEqualByComparingTo("1000.00");
        assertThat(outbound.getItems().get(0).getAmount()).isEqualByComparingTo("1500.00");
        assertThat(outbound.getTotalAmount()).isEqualByComparingTo("1500.00");
        verify(repository).saveAll(List.of(outbound));
    }

    @Test
    void shouldSyncDraftOutboundPricingBeforeAudit() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutbound outbound = outbound(101L, new BigDecimal("2.000"));
        outbound.setStatus(StatusConstants.DRAFT);
        when(repository.findAllByStatusesAndSourceSalesOrderItemIds(
                eq(List.of(StatusConstants.DRAFT, StatusConstants.AUDITED)),
                eq(List.of(101L))
        )).thenReturn(List.of(outbound));
        SalesOutboundPricingSyncService service = new SalesOutboundPricingSyncService(repository);

        service.syncAuditedOutboundPricing(List.of(101L), Map.of(101L, new BigDecimal("4200.00")));

        assertThat(outbound.getItems().get(0).getUnitPrice()).isEqualByComparingTo("4200.00");
        assertThat(outbound.getTotalAmount()).isEqualByComparingTo("8400.00");
        verify(repository).saveAll(List.of(outbound));
    }

    private SalesOutbound outbound(Long sourceSalesOrderItemId, BigDecimal weightTon) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setOutboundNo("CK-001");
        outbound.setStatus(StatusConstants.AUDITED);

        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(11L);
        item.setSalesOutbound(outbound);
        item.setLineNo(1);
        item.setSourceSalesOrderItemId(sourceSalesOrderItemId);
        item.setWeightTon(weightTon);
        item.setUnitPrice(BigDecimal.ZERO);
        item.setAmount(BigDecimal.ZERO);
        outbound.setItems(List.of(item));
        outbound.setTotalAmount(BigDecimal.ZERO);
        return outbound;
    }
}
