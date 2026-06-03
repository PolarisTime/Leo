package com.leo.erp.sales.order.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderItemRequestTest {

    @Test
    void shouldCreateWithFullConstructor() {
        SalesOrderItemRequest request = new SalesOrderItemRequest(
                1L, "M1", "宝钢", "盘螺", "HRB400", "8", "12m", "吨",
                101L, 201L, "一号库", "B1", 5, "件",
                new BigDecimal("2.248"), 2, new BigDecimal("11.240"),
                new BigDecimal("3000.00"), new BigDecimal("33720.00")
        );

        assertThat(request.id()).isEqualTo(1L);
        assertThat(request.materialCode()).isEqualTo("M1");
        assertThat(request.brand()).isEqualTo("宝钢");
        assertThat(request.category()).isEqualTo("盘螺");
        assertThat(request.material()).isEqualTo("HRB400");
        assertThat(request.spec()).isEqualTo("8");
        assertThat(request.length()).isEqualTo("12m");
        assertThat(request.unit()).isEqualTo("吨");
        assertThat(request.sourceInboundItemId()).isEqualTo(101L);
        assertThat(request.sourcePurchaseOrderItemId()).isEqualTo(201L);
        assertThat(request.warehouseName()).isEqualTo("一号库");
        assertThat(request.batchNo()).isEqualTo("B1");
        assertThat(request.quantity()).isEqualTo(5);
        assertThat(request.quantityUnit()).isEqualTo("件");
        assertThat(request.pieceWeightTon()).isEqualByComparingTo("2.248");
        assertThat(request.piecesPerBundle()).isEqualTo(2);
        assertThat(request.weightTon()).isEqualByComparingTo("11.240");
        assertThat(request.unitPrice()).isEqualByComparingTo("3000.00");
        assertThat(request.amount()).isEqualByComparingTo("33720.00");
    }

    @Test
    void shouldCreateWithConvenienceConstructorWithoutId() {
        SalesOrderItemRequest request = new SalesOrderItemRequest(
                "M1", "宝钢", "盘螺", "HRB400", "8", "12m", "吨",
                101L, 201L, "一号库", "B1", 5, "件",
                new BigDecimal("2.248"), 2, new BigDecimal("11.240"),
                new BigDecimal("3000.00"), new BigDecimal("33720.00")
        );

        assertThat(request.id()).isNull();
        assertThat(request.materialCode()).isEqualTo("M1");
    }

    @Test
    void shouldCreateWithInboundOnlyConstructor() {
        SalesOrderItemRequest request = new SalesOrderItemRequest(
                "M1", "宝钢", "盘螺", "HRB400", "8", "12m", "吨",
                101L, "一号库", "B1", 5, "件",
                new BigDecimal("2.248"), 2, new BigDecimal("11.240"),
                new BigDecimal("3000.00"), new BigDecimal("33720.00")
        );

        assertThat(request.sourceInboundItemId()).isEqualTo(101L);
        assertThat(request.sourcePurchaseOrderItemId()).isNull();
    }
}
