package com.leo.erp.purchase.order.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderItemResponseTest {

    @Test
    void legacyConstructorShouldDefaultSettlementCompanyFields() {
        PurchaseOrderItemResponse response = new PurchaseOrderItemResponse(
                1L,
                2,
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "9m",
                "吨",
                "一号库",
                "B1",
                8,
                7,
                new BigDecimal("0.700"),
                10,
                "支",
                new BigDecimal("0.100"),
                1,
                new BigDecimal("1.000"),
                new BigDecimal("0.990"),
                new BigDecimal("0.099"),
                new BigDecimal("4000.00"),
                new BigDecimal("4000.00")
        );

        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
        assertThat(response.warehouseName()).isEqualTo("一号库");
        assertThat(response.salesRemainingWeightTon()).isEqualByComparingTo("0.700");
    }
}
