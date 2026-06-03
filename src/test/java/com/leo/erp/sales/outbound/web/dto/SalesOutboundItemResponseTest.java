package com.leo.erp.sales.outbound.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOutboundItemResponseTest {

    @Test
    void shouldCreateWithAllFields() {
        SalesOutboundItemResponse response = new SalesOutboundItemResponse(
                1L, 1, "SO-001", 201L,
                "M1", "宝钢", "盘螺", "HRB400", "8", "12m", "吨",
                "一号库", "B1", 5, "件",
                new BigDecimal("2.248"), 2, new BigDecimal("11.240"),
                new BigDecimal("3000.00"), new BigDecimal("33720.00")
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.lineNo()).isEqualTo(1);
        assertThat(response.sourceNo()).isEqualTo("SO-001");
        assertThat(response.sourceSalesOrderItemId()).isEqualTo(201L);
        assertThat(response.materialCode()).isEqualTo("M1");
        assertThat(response.brand()).isEqualTo("宝钢");
        assertThat(response.category()).isEqualTo("盘螺");
        assertThat(response.material()).isEqualTo("HRB400");
        assertThat(response.spec()).isEqualTo("8");
        assertThat(response.length()).isEqualTo("12m");
        assertThat(response.unit()).isEqualTo("吨");
        assertThat(response.warehouseName()).isEqualTo("一号库");
        assertThat(response.batchNo()).isEqualTo("B1");
        assertThat(response.quantity()).isEqualTo(5);
        assertThat(response.quantityUnit()).isEqualTo("件");
        assertThat(response.pieceWeightTon()).isEqualByComparingTo("2.248");
        assertThat(response.piecesPerBundle()).isEqualTo(2);
        assertThat(response.weightTon()).isEqualByComparingTo("11.240");
        assertThat(response.unitPrice()).isEqualByComparingTo("3000.00");
        assertThat(response.amount()).isEqualByComparingTo("33720.00");
    }
}
