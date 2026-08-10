package com.leo.erp.sales.outbound.service;

import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SalesOutboundWeightService 极端情况测试。
 */
class SalesOutboundWeightServiceTest {

    private final SalesOutboundWeightService service = new SalesOutboundWeightService();

    private SalesOutboundItemRequest request(BigDecimal weightTon, Integer quantity, BigDecimal pieceWeightTon) {
        return new SalesOutboundItemRequest(
                null, "SO001", 11L, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m",
                "吨", 1L, "库房A", "B001", quantity, "件", pieceWeightTon, 100, weightTon,
                new BigDecimal("4000.00"), null);
    }

    private SalesOrderItem sourceItem(Integer quantity, BigDecimal weightTon) {
        SalesOrderItem item = new SalesOrderItem();
        item.setQuantity(quantity);
        item.setWeightTon(weightTon);
        return item;
    }

    @Test
    void shouldUseProvidedWeightTonDirectly() {
        BigDecimal result = service.resolveOutboundWeightTon(
                request(new BigDecimal("15.000"), 10, new BigDecimal("1.000")), sourceItem(10, new BigDecimal("12.000")), 11L, 1);

        assertThat(result).isEqualByComparingTo("15.000");
    }

    @Test
    void shouldFallbackWhenSourceItemIdNull() {
        BigDecimal result = service.resolveOutboundWeightTon(
                request(null, 10, new BigDecimal("2.500")), sourceItem(10, new BigDecimal("12.000")), null, 1);

        // 无来源 ID → 用请求件重×数量
        assertThat(result).isEqualByComparingTo("25.000");
    }

    @Test
    void shouldFallbackWhenQuantityNull() {
        BigDecimal result = service.resolveOutboundWeightTon(
                request(null, null, new BigDecimal("2.500")), sourceItem(10, new BigDecimal("12.000")), 11L, 1);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void shouldFallbackWhenQuantityZero() {
        BigDecimal result = service.resolveOutboundWeightTon(
                request(null, 0, new BigDecimal("2.500")), sourceItem(10, new BigDecimal("12.000")), 11L, 1);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void shouldThrowWhenSourceItemNull() {
        assertThatThrownBy(() -> service.resolveOutboundWeightTon(
                request(null, 10, new BigDecimal("1.000")), null, 11L, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("第3行");
    }

    @Test
    void shouldThrowWhenSourceItemQuantityNull() {
        assertThatThrownBy(() -> service.resolveOutboundWeightTon(
                request(null, 10, new BigDecimal("1.000")), sourceItem(null, new BigDecimal("12.000")), 11L, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenSourceItemWeightNull() {
        assertThatThrownBy(() -> service.resolveOutboundWeightTon(
                request(null, 10, new BigDecimal("1.000")), sourceItem(10, null), 11L, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCalculateFromSourceAveragePieceWeight() {
        // 来源总重 12 吨 / 数量 4 = 单件 3 吨；请求数量 2 → 6 吨
        BigDecimal result = service.resolveOutboundWeightTon(
                request(null, 2, new BigDecimal("1.000")), sourceItem(4, new BigDecimal("12.000")), 11L, 1);

        assertThat(result).isEqualByComparingTo("6.000");
    }

    @Test
    void shouldThrowWhenSourceItemQuantityZero() {
        assertThatThrownBy(() -> service.resolveOutboundWeightTon(
                request(null, 10, new BigDecimal("1.000")), sourceItem(0, new BigDecimal("12.000")), 11L, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
