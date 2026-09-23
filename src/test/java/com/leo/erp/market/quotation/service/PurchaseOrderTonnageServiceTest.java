package com.leo.erp.market.quotation.service;

import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.PurchaseOrderTonnageResponse;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery.PurchaseOrderOptionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    void summarize_computesRemainingFromIssued() {
        when(purchaseOrderOptionQuery.listActiveByIds(any()))
                .thenReturn(List.of(
                        new PurchaseOrderOptionSnapshot(1L, "PO-1", "沙钢", new BigDecimal("40.5"), "正常", null),
                        new PurchaseOrderOptionSnapshot(2L, "PO-2", "中天", new BigDecimal("10"), "正常", null)));
        when(quoteSheetRepository.sumIssuedTonByPurchaseOrderIds(any(), eq(7L))).thenReturn(List.<Object[]>of(
                new Object[]{1L, new BigDecimal("30.5")},
                new Object[]{2L, null}));

        List<PurchaseOrderTonnageResponse> result = service().summarize(List.of(1L, 2L), 7L);

        assertThat(result).hasSize(2);
        PurchaseOrderTonnageResponse first = result.get(0);
        assertThat(first.orderedWeight()).isEqualByComparingTo("40.5");
        assertThat(first.issuedWeight()).isEqualByComparingTo("30.5");
        assertThat(first.remainingWeight()).isEqualByComparingTo("10");
        PurchaseOrderTonnageResponse second = result.get(1);
        assertThat(second.issuedWeight()).isEqualByComparingTo("0");
        assertThat(second.remainingWeight()).isEqualByComparingTo("10");
    }

    @Test
    void summarize_overIssued_returnsNegativeRemaining() {
        when(purchaseOrderOptionQuery.listActiveByIds(any()))
                .thenReturn(List.of(
                        new PurchaseOrderOptionSnapshot(1L, "PO-1", "沙钢", new BigDecimal("10"), "正常", null)));
        when(quoteSheetRepository.sumIssuedTonByPurchaseOrderIds(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{1L, new BigDecimal("12.5")}));

        List<PurchaseOrderTonnageResponse> result = service().summarize(List.of(1L), null);

        assertThat(result.get(0).remainingWeight()).isEqualByComparingTo("-2.5");
    }

    @Test
    void summarize_emptyIds_returnsEmptyWithoutQuerying() {
        assertThat(service().summarize(List.of(), null)).isEmpty();
        assertThat(service().summarize(null, null)).isEmpty();
        verify(quoteSheetRepository, never()).sumIssuedTonByPurchaseOrderIds(any(), any());
    }

    @Test
    void listOptions_delegatesKeywordAndStatus() {
        when(purchaseOrderOptionQuery.listActiveOptions("沙", "正常"))
                .thenReturn(List.of(
                        new PurchaseOrderOptionSnapshot(1L, "PO-1", "沙钢", new BigDecimal("5"), "正常", null)));
        when(quoteSheetRepository.sumIssuedTonByPurchaseOrderIds(any(), any())).thenReturn(List.of());

        List<PurchaseOrderTonnageResponse> result = service().listOptions("沙", "正常", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).remainingWeight()).isEqualByComparingTo("5");
        verify(purchaseOrderOptionQuery).listActiveOptions("沙", "正常");
    }

    @Test
    void summarize_deduplicatesOrderIds() {
        when(purchaseOrderOptionQuery.listActiveByIds(any())).thenReturn(List.of());
        service().summarize(List.of(1L, 1L, 2L), null);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Collection<Long>> captor =
                org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(purchaseOrderOptionQuery).listActiveByIds(captor.capture());
        assertThat(captor.getValue()).containsExactly(1L, 2L);
    }
}
