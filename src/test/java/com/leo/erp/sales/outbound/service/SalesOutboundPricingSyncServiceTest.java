package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.StatusConstants;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOutboundPricingSyncService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOutboundPricingSyncServiceTest {

    @Mock
    private SalesOutboundRepository salesOutboundRepository;

    @InjectMocks
    private SalesOutboundPricingSyncService service;

    private SalesOutboundItem item(Long sourceId, String weightTon, String amount) {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(sourceId);
        item.setWeightTon(weightTon == null ? null : new BigDecimal(weightTon));
        item.setAmount(amount == null ? null : new BigDecimal(amount));
        return item;
    }

    @Test
    void shouldSkipWhenSourceIdsNull() {
        service.syncAuditedOutboundPricing(null, Map.of(1L, new BigDecimal("10")));
        verifyNoInteractions(salesOutboundRepository);
    }

    @Test
    void shouldSkipWhenSourceIdsEmpty() {
        service.syncAuditedOutboundPricing(List.of(), Map.of(1L, new BigDecimal("10")));
        verifyNoInteractions(salesOutboundRepository);
    }

    @Test
    void shouldSkipWhenPriceMapNull() {
        service.syncAuditedOutboundPricing(List.of(1L), null);
        verifyNoInteractions(salesOutboundRepository);
    }

    @Test
    void shouldSkipWhenPriceMapEmpty() {
        service.syncAuditedOutboundPricing(List.of(1L), Map.of());
        verifyNoInteractions(salesOutboundRepository);
    }

    @Test
    void shouldSkipWhenNoOutbounds() {
        when(salesOutboundRepository.findAllByStatusesAndSourceSalesOrderItemIds(any(), any()))
                .thenReturn(List.of());

        service.syncAuditedOutboundPricing(List.of(1L), Map.of(1L, new BigDecimal("100")));

        verify(salesOutboundRepository).findAllByStatusesAndSourceSalesOrderItemIds(
                List.of(StatusConstants.DRAFT, StatusConstants.AUDITED), List.of(1L));
    }

    @Test
    void shouldUpdatePricingForMatchingItemsAndRecalculateAmounts() {
        SalesOutboundItem matched = item(11L, "10.000", null);
        SalesOutboundItem unmatched = item(22L, "5.000", "25.00");
        SalesOutbound outbound = new SalesOutbound();
        outbound.setItems(List.of(matched, unmatched));
        when(salesOutboundRepository.findAllByStatusesAndSourceSalesOrderItemIds(any(), any()))
                .thenReturn(List.of(outbound));

        service.syncAuditedOutboundPricing(List.of(11L), Map.of(11L, new BigDecimal("100")));

        // 匹配项：unitPrice 更新，amount = weight * unitPrice = 10 * 100 = 1000
        assertThat(matched.getUnitPrice()).isEqualByComparingTo("100");
        assertThat(matched.getAmount()).isEqualByComparingTo("1000");
        // 未匹配项：unitPrice 保留，amount 保留
        assertThat(unmatched.getUnitPrice()).isNull();
        // 总额 = 1000 + 25 = 1025
        assertThat(outbound.getTotalAmount()).isEqualByComparingTo("1025");
        verify(salesOutboundRepository).saveAll(List.of(outbound));
    }
}
