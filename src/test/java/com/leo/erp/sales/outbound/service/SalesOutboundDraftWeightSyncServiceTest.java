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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOutboundDraftWeightSyncService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOutboundDraftWeightSyncServiceTest {

    @Mock
    private SalesOutboundRepository repository;

    @InjectMocks
    private SalesOutboundDraftWeightSyncService service;

    @Test
    void shouldSkipWhenWeightsNull() {
        service.syncBySalesOrderItemWeights(null);
        verifyNoInteractions(repository);
    }

    @Test
    void shouldSkipWhenWeightsEmpty() {
        service.syncBySalesOrderItemWeights(Map.of());
        verifyNoInteractions(repository);
    }

    @Test
    void shouldSkipWhenAllWeightsHaveNullKeyOrValue() {
        Map<Long, BigDecimal> weights = new HashMap<>();
        weights.put(null, new BigDecimal("10"));
        weights.put(1L, null);

        service.syncBySalesOrderItemWeights(weights);

        verifyNoInteractions(repository);
    }

    @Test
    void shouldApplyWeightsAndRecalculateAmounts() {
        SalesOutboundItem matched = new SalesOutboundItem();
        matched.setSourceSalesOrderItemId(11L);
        matched.setWeightTon(new BigDecimal("9.000"));
        matched.setUnitPrice(new BigDecimal("100"));
        SalesOutbound outbound = new SalesOutbound();
        outbound.setItems(List.of(matched));
        when(repository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(eq(StatusConstants.DRAFT), any()))
                .thenReturn(List.of(outbound));

        service.syncBySalesOrderItemWeights(Map.of(11L, new BigDecimal("12.500")));

        assertThat(matched.getWeightTon()).isEqualByComparingTo("12.500");
        // amount = weight * unitPrice = 12.5 * 100
        assertThat(matched.getAmount()).isEqualByComparingTo("1250");
        assertThat(outbound.getTotalWeight()).isEqualByComparingTo("12.500");
        assertThat(outbound.getTotalAmount()).isEqualByComparingTo("1250");
        verify(repository).saveAll(List.of(outbound));
    }

    @Test
    void shouldKeepOriginalWeightWhenNoSourceMatch() {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(99L);
        item.setWeightTon(new BigDecimal("7.000"));
        item.setUnitPrice(new BigDecimal("50"));
        SalesOutbound outbound = new SalesOutbound();
        outbound.setItems(List.of(item));
        when(repository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(eq(StatusConstants.DRAFT), any()))
                .thenReturn(List.of(outbound));

        service.syncBySalesOrderItemWeights(Map.of(11L, new BigDecimal("12.500")));

        // 来源重量不匹配 → 保留原重量，仅重算金额
        assertThat(item.getWeightTon()).isEqualByComparingTo("7.000");
        assertThat(item.getAmount()).isEqualByComparingTo("350");
        assertThat(outbound.getTotalWeight()).isEqualByComparingTo("7.000");
    }
}
