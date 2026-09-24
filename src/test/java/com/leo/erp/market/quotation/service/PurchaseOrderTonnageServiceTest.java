package com.leo.erp.market.quotation.service;

import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.PurchaseOrderTonnageResponse;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery.PurchaseOrderItemOptionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderTonnageServiceTest {

    @Mock
    private QuoteSheetRepository quoteSheetRepository;

    @Mock
    private PurchaseOrderOptionQuery purchaseOrderOptionQuery;

    private PurchaseOrderTonnageService service() {
        return new PurchaseOrderTonnageService(quoteSheetRepository, purchaseOrderOptionQuery);
    }

    private static PurchaseOrderItemOptionSnapshot item(long orderId, long itemId, String orderNo,
                                                         String spec, String orderedWeight) {
        return new PurchaseOrderItemOptionSnapshot(
                orderId, itemId, orderNo, "沙钢", "螺纹钢", "HRB400E", spec, "9米",
                new BigDecimal(orderedWeight), "正常");
    }

    @Test
    void summarize_computesRemainingFromIssuedPerSpec() {
        when(purchaseOrderOptionQuery.listActiveItemsByIds(any()))
                .thenReturn(List.of(
                        item(1L, 11L, "PO-1", "12", "40.5"),
                        item(1L, 12L, "PO-1", "25", "10")));
        when(quoteSheetRepository.sumIssuedTonByPurchaseOrderItemIds(any(), eq(7L))).thenReturn(List.<Object[]>of(
                new Object[]{11L, new BigDecimal("30.5")},
                new Object[]{12L, null}));

        List<PurchaseOrderTonnageResponse> result = service().summarize(List.of(11L, 12L), 7L);

        assertThat(result).hasSize(2);
        PurchaseOrderTonnageResponse first = result.get(0);
        assertThat(first.purchaseOrderItemId()).isEqualTo(11L);
        assertThat(first.spec()).isEqualTo("12");
        assertThat(first.orderedWeight()).isEqualByComparingTo("40.5");
        assertThat(first.issuedWeight()).isEqualByComparingTo("30.5");
        assertThat(first.remainingWeight()).isEqualByComparingTo("10");
        PurchaseOrderTonnageResponse second = result.get(1);
        assertThat(second.issuedWeight()).isEqualByComparingTo("0");
        assertThat(second.remainingWeight()).isEqualByComparingTo("10");
    }

    @Test
    void summarize_sameOrderDifferentSpecs_areIndependent() {
        // 同一订单两条规格: Φ12 超开, Φ25 未开, 互不影响
        when(purchaseOrderOptionQuery.listActiveItemsByIds(any()))
                .thenReturn(List.of(
                        item(1L, 11L, "PO-1", "12", "5"),
                        item(1L, 12L, "PO-1", "25", "10")));
        when(quoteSheetRepository.sumIssuedTonByPurchaseOrderItemIds(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{11L, new BigDecimal("8")}));

        List<PurchaseOrderTonnageResponse> result = service().summarize(List.of(11L, 12L), null);

        assertThat(result.get(0).remainingWeight()).isEqualByComparingTo("-3");
        assertThat(result.get(1).remainingWeight()).isEqualByComparingTo("10");
    }

    @Test
    void summarize_overIssued_returnsNegativeRemaining() {
        when(purchaseOrderOptionQuery.listActiveItemsByIds(any()))
                .thenReturn(List.of(item(1L, 11L, "PO-1", "12", "10")));
        when(quoteSheetRepository.sumIssuedTonByPurchaseOrderItemIds(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{11L, new BigDecimal("12.5")}));

        List<PurchaseOrderTonnageResponse> result = service().summarize(List.of(11L), null);

        assertThat(result.get(0).remainingWeight()).isEqualByComparingTo("-2.5");
    }

    @Test
    void summarize_emptyIds_returnsEmptyWithoutQuerying() {
        assertThat(service().summarize(List.of(), null)).isEmpty();
        assertThat(service().summarize(null, null)).isEmpty();
        verify(quoteSheetRepository, never()).sumIssuedTonByPurchaseOrderItemIds(any(), any());
    }

    @Test
    void listOptions_delegatesKeywordStatusAndOrder() {
        when(purchaseOrderOptionQuery.listActiveItemOptions("沙", "正常", 88L))
                .thenReturn(List.of(item(88L, 11L, "PO-88", "12", "5")));
        when(quoteSheetRepository.sumIssuedTonByPurchaseOrderItemIds(any(), any()))
                .thenReturn(List.of());

        List<PurchaseOrderTonnageResponse> result =
                service().listOptions("沙", "正常", 88L, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).remainingWeight()).isEqualByComparingTo("5");
        verify(purchaseOrderOptionQuery).listActiveItemOptions("沙", "正常", 88L);
    }

    @Test
    void summarize_deduplicatesItemIds() {
        when(purchaseOrderOptionQuery.listActiveItemsByIds(any())).thenReturn(List.of());
        service().summarize(List.of(1L, 1L, 2L), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(purchaseOrderOptionQuery).listActiveItemsByIds(captor.capture());
        assertThat(captor.getValue()).containsExactly(1L, 2L);
    }
}
