package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderResponseAssemblerTest {

    private PurchaseOrderItemResponse item(Integer remainingQuantity) {
        return new PurchaseOrderItemResponse(
                1L,
                1,
                "M-1",
                "品牌",
                "品类",
                "材质",
                "规格",
                null,
                "吨",
                "仓库",
                "B-1",
                remainingQuantity,
                0,
                BigDecimal.ZERO,
                10,
                "件",
                BigDecimal.ONE,
                1,
                BigDecimal.TEN,
                null,
                BigDecimal.ONE,
                BigDecimal.TEN
        );
    }

    @Test
    void totalRemainingQuantity_shouldSumRemainingAcrossLines() {
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(
                List.of(item(2), item(3)))).isEqualTo(5);
    }

    @Test
    void totalRemainingQuantity_shouldReturnZeroForNullOrEmptyOrAllZero() {
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(null)).isZero();
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(List.of())).isZero();
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(
                List.of(item(0), item(0)))).isZero();
    }

    /** 负值（异常数据）按 0 计，不得拉低总和。 */
    @Test
    void totalRemainingQuantity_shouldClampNegativeLinesToZero() {
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(
                List.of(item(4), item(-3)))).isEqualTo(4);
    }

    @Test
    void totalRemainingQuantity_shouldIgnoreNullRemainingAndNullEntries() {
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(
                List.of(item(null), item(5)))).isEqualTo(5);
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(
                java.util.Arrays.asList(item(3), null))).isEqualTo(3);
    }

    /** 总量超过 int 上界时收敛到 Integer.MAX_VALUE，避免回绕为负数。 */
    @Test
    void totalRemainingQuantity_shouldSaturateOnOverflow() {
        assertThat(PurchaseOrderResponseAssembler.totalRemainingQuantity(
                List.of(item(Integer.MAX_VALUE), item(Integer.MAX_VALUE))))
                .isEqualTo(Integer.MAX_VALUE);
    }

    /** 便捷构造默认不带实际货值与差额，交由 applyReferenceFlags 链路补齐。 */
    @Test
    void convenienceConstructor_shouldDefaultDifferenceFieldsToNull() {
        PurchaseOrderResponse response = new PurchaseOrderResponse(
                1L, "PO-1", null, null, "供应商", null, null, null, null,
                null, null, "已审核", false, null, List.of()
        );

        assertThat(response.totalActualAmount()).isNull();
        assertThat(response.totalAmountDifference()).isNull();
        assertThat(response.totalRemainingQuantity()).isNull();
    }

    /** applyDifference 仅替换实际货值与差额，保留既有未入库件数。 */
    @Test
    void applyDifference_shouldReplaceOnlyDifferenceFields() {
        PurchaseOrderResponse base = new PurchaseOrderResponse(
                1L, "PO-1", null, null, "供应商", null, null, null, null,
                null, null, "已审核", false, null, List.of()
        ).applyTotalRemainingQuantity(7);

        PurchaseOrderResponse applied = base.applyDifference(
                new BigDecimal("1200.50"), new BigDecimal("-200.50"));

        assertThat(applied.totalActualAmount()).isEqualByComparingTo("1200.50");
        assertThat(applied.totalAmountDifference()).isEqualByComparingTo("-200.50");
        assertThat(applied.totalRemainingQuantity()).isEqualTo(7);
    }

    /** applyDifference 透传 null 值。 */
    @Test
    void applyDifference_shouldPassthroughNulls() {
        PurchaseOrderResponse base = new PurchaseOrderResponse(
                1L, "PO-1", null, null, "供应商", null, null, null, null,
                null, null, "已审核", false, null, List.of()
        );

        PurchaseOrderResponse applied = base.applyDifference(null, null);

        assertThat(applied.totalActualAmount()).isNull();
        assertThat(applied.totalAmountDifference()).isNull();
    }
}
