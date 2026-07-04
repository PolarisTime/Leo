package com.leo.erp.purchase.inbound.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseInboundItemRequestTest {

    @Test
    void legacyConstructorWithIdShouldDefaultSettlementModeToNull() {
        PurchaseInboundItemRequest request = new PurchaseInboundItemRequest(
                1L,
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "9m",
                "吨",
                101L,
                "一号库",
                "B1",
                3,
                "支",
                new BigDecimal("0.400"),
                1,
                new BigDecimal("1.200"),
                new BigDecimal("1.210"),
                new BigDecimal("0.010"),
                new BigDecimal("40.00"),
                new BigDecimal("4000.00"),
                new BigDecimal("4800.00")
        );

        assertThat(request.id()).isEqualTo(1L);
        assertThat(request.settlementMode()).isNull();
        assertThat(request.weightAdjustmentAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void legacyConstructorWithoutIdShouldDefaultIdAndWeightAdjustmentFields() {
        PurchaseInboundItemRequest request = new PurchaseInboundItemRequest(
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "9m",
                "吨",
                101L,
                "一号库",
                "B1",
                3,
                "支",
                new BigDecimal("0.400"),
                1,
                new BigDecimal("1.200"),
                new BigDecimal("4000.00"),
                new BigDecimal("4800.00")
        );

        assertThat(request.id()).isNull();
        assertThat(request.settlementMode()).isNull();
        assertThat(request.weighWeightTon()).isNull();
        assertThat(request.weightAdjustmentTon()).isNull();
        assertThat(request.weightAdjustmentAmount()).isNull();
    }
}
