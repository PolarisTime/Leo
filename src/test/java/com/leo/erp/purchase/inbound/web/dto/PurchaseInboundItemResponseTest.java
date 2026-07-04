package com.leo.erp.purchase.inbound.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseInboundItemResponseTest {

    @Test
    void shouldDefaultSettlementCompanyWhenUsingLegacyConstructor() {
        PurchaseInboundItemResponse response = new PurchaseInboundItemResponse(
                1L,
                2,
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                101L,
                "一号库",
                "理算",
                "B1",
                8,
                2,
                "支",
                new BigDecimal("0.100"),
                1,
                new BigDecimal("0.200"),
                new BigDecimal("0.210"),
                new BigDecimal("0.010"),
                new BigDecimal("40.00"),
                new BigDecimal("4000.00"),
                new BigDecimal("800.00")
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.lineNo()).isEqualTo(2);
        assertThat(response.materialCode()).isEqualTo("M1");
        assertThat(response.sourcePurchaseOrderItemId()).isEqualTo(101L);
        assertThat(response.settlementCompanyId()).isNull();
        assertThat(response.settlementCompanyName()).isNull();
        assertThat(response.warehouseName()).isEqualTo("一号库");
        assertThat(response.settlementMode()).isEqualTo("理算");
        assertThat(response.remainingQuantity()).isEqualTo(8);
        assertThat(response.weightTon()).isEqualByComparingTo("0.200");
        assertThat(response.amount()).isEqualByComparingTo("800.00");
    }
}
