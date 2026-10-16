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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
                orderId, itemId, orderNo, "沙钢", "螺纹钢", "HRB400E", spec, "9米", "中天",
                new BigDecimal(orderedWeight), null, false, "正常");
    }

    /** 带真实已开货(入库回写值)与手动标记的明细行快照。 */
    private static PurchaseOrderItemOptionSnapshot shippedItem(long orderId, long itemId, String orderNo,
                                                               String spec, String orderedWeight,
                                                               String actualWeightTon, boolean issuedDone) {
        return new PurchaseOrderItemOptionSnapshot(
                orderId, itemId, orderNo, "沙钢", "螺纹钢", "HRB400E", spec, "9米", "中天",
                new BigDecimal(orderedWeight),
                actualWeightTon == null ? null : new BigDecimal(actualWeightTon),
                issuedDone, "正常");
    }

    @Test
    void summarize_carriesItemBrandForIssuedBrandDisplay() {
        when(purchaseOrderOptionQuery.listActiveItemsByIds(any()))
                .thenReturn(List.of(item(1L, 11L, "PO-1", "12", "40.5")));
        when(quoteSheetRepository.sumIssuedTonByPurchaseOrderItemIds(any(), any()))
                .thenReturn(List.of());

        List<PurchaseOrderTonnageResponse> result = service().summarize(List.of(11L), null);

        // 品牌沿用订单明细自带字段, 前端据此展示"已开的品牌"
        assertThat(result.get(0).brand()).isEqualTo("中天");
    }

    @Test
    void summarize_computesRemainingFromIssuedPerSpec() {
        when(purchaseOrderOptionQuery.listActiveItemsByIds(any()))
                .thenReturn(List.of(
                        item(1L, 11L, "PO-1", "12", "40.5"),
                        item(1L, 12L, "PO-1", "25", "10")));
        when(quoteSheetRepository.sumIssuedTonByPurchaseOrderItemIds(any(), eq(7L)))
                .thenReturn(List.<Object[]>of(
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

    /**
     * P2-18 回归: 两段口径(关联分配量 + 历史旧列兜底)必须由**一次**仓储调用读完。
     * <p>拆成两次查询再在内存相加时, READ COMMITTED 下另一事务在两条语句之间提交
     * (把旧单值行改写成关联行这类转换), 该行会两段都不计而瞬时少计。这里以"只调用一次
     * 汇总方法、不产生第二次汇总调用"钉住单语句/单快照行为(并发时序难以稳定复现, 故用调用次数)。
     */
    @Test
    void summarize_readsIssuedTonInSingleStatementForSingleSnapshot() {
        when(purchaseOrderOptionQuery.listActiveItemsByIds(any()))
                .thenReturn(List.of(item(1L, 11L, "PO-1", "12", "40.5")));
        when(quoteSheetRepository.sumIssuedTonByPurchaseOrderItemIds(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{11L, new BigDecimal("30.5")}));

        List<PurchaseOrderTonnageResponse> result = service().summarize(List.of(11L), null);

        assertThat(result.get(0).issuedWeight()).isEqualByComparingTo("30.5");
        verify(quoteSheetRepository, times(1)).sumIssuedTonByPurchaseOrderItemIds(any(), any());
        // 任何第二次汇总调用都意味着又开了一条语句 → 两段口径不在同一快照里
        verifyNoMoreInteractions(quoteSheetRepository);
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

    /**
     * 「报单占用」与「真实已开货」是两个不同的量, 必须同时下发且不得互相顶替。
     *
     * <p>本用例是那次踩坑的回归钉: 报价单只在订单行上"报"了 5 吨(issuedWeight=5), 但该订单行
     * 真实入库 2.3 吨(actualWeightTon=2.3)。若把已开列接到 issuedWeight 上, 页面就会把
     * "别家报单量"当成"已经开出去的货"; 反之若把选单额度接到 actualWeightTon 上, 入库为 0
     * 的行会被无限重复关联。两个出口必须各自取自己的字段。</p>
     */
    @Test
    void summarize_carriesActualWeightAndIssuedDoneSeparatelyFromIssuedWeight() {
        when(purchaseOrderOptionQuery.listActiveItemsByIds(any()))
                .thenReturn(List.of(shippedItem(1L, 11L, "PO-1", "12", "32.768", "32.768", true)));
        when(quoteSheetRepository.sumIssuedTonByPurchaseOrderItemIds(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{11L, new BigDecimal("17")}));

        PurchaseOrderTonnageResponse result = service().summarize(List.of(11L), null).get(0);

        // 报单占用(选单额度口径): 别家报价单报了多少
        assertThat(result.issuedWeight()).isEqualByComparingTo("17");
        assertThat(result.remainingWeight()).isEqualByComparingTo("15.768");
        // 真实已开货(吨位列口径): 入库回写值, 与报单占用无关
        assertThat(result.actualWeightTon()).isEqualByComparingTo("32.768");
        assertThat(result.actualWeightTon()).isNotEqualByComparingTo(result.issuedWeight());
        // 人工裁定标记原样透传, 不由吨位推导
        assertThat(result.issuedDone()).isTrue();
    }

    /** 尚未开货时 actualWeightTon 保持 null(要能区分"开了 0 吨"与"没有任何到货记录")。 */
    @Test
    void summarize_keepsActualWeightNullWhenNothingShipped() {
        when(purchaseOrderOptionQuery.listActiveItemsByIds(any()))
                .thenReturn(List.of(shippedItem(1L, 11L, "PO-1", "12", "2.3", null, false)));
        when(quoteSheetRepository.sumIssuedTonByPurchaseOrderItemIds(any(), any()))
                .thenReturn(List.of());

        PurchaseOrderTonnageResponse result = service().summarize(List.of(11L), null).get(0);

        assertThat(result.actualWeightTon()).isNull();
        assertThat(result.issuedDone()).isFalse();
    }
}
