package com.leo.erp.sales.order.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderResponseTest {

    @Test
    void shouldCreateWithFullConstructor() {
        SalesOrderResponse response = new SalesOrderResponse(
                1L, "SO-001", "PI-001", "PO-001", "C001", "客户A", 100L, "项目A",
                LocalDate.of(2026, 5, 1), "张三",
                new BigDecimal("10.500"), new BigDecimal("31500.00"),
                "草稿", "备注", List.of()
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.orderNo()).isEqualTo("SO-001");
        assertThat(response.purchaseInboundNo()).isEqualTo("PI-001");
        assertThat(response.purchaseOrderNo()).isEqualTo("PO-001");
        assertThat(response.customerCode()).isEqualTo("C001");
        assertThat(response.customerName()).isEqualTo("客户A");
        assertThat(response.projectId()).isEqualTo(100L);
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.deliveryDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(response.salesName()).isEqualTo("张三");
        assertThat(response.totalWeight()).isEqualByComparingTo("10.500");
        assertThat(response.totalAmount()).isEqualByComparingTo("31500.00");
        assertThat(response.status()).isEqualTo("草稿");
        assertThat(response.remark()).isEqualTo("备注");
        assertThat(response.items()).isEmpty();
    }

    @Test
    void shouldCreateWithConvenienceConstructorWithoutPurchaseOrderNo() {
        SalesOrderResponse response = new SalesOrderResponse(
                1L, "SO-001", "PI-001", "C001", "客户A", 100L, "项目A",
                LocalDate.of(2026, 5, 1), "张三",
                new BigDecimal("10.500"), new BigDecimal("31500.00"),
                "草稿", "备注", List.of()
        );

        assertThat(response.purchaseOrderNo()).isNull();
        assertThat(response.orderNo()).isEqualTo("SO-001");
    }

    @Test
    void shouldCreateWithShortConvenienceConstructor() {
        SalesOrderResponse response = new SalesOrderResponse(
                1L, "SO-001", "PI-001", "客户A", "项目A",
                LocalDate.of(2026, 5, 1), "张三",
                new BigDecimal("10.500"), new BigDecimal("31500.00"),
                "草稿", "备注", List.of()
        );

        assertThat(response.purchaseOrderNo()).isNull();
        assertThat(response.customerCode()).isNull();
        assertThat(response.projectId()).isNull();
    }

    @Test
    void shouldCreateWithPurchaseOrderNoConstructor() {
        SalesOrderResponse response = new SalesOrderResponse(
                1L, "SO-001", "PI-001", "PO-001", "客户A", "项目A",
                LocalDate.of(2026, 5, 1), "张三",
                new BigDecimal("10.500"), new BigDecimal("31500.00"),
                "草稿", "备注", List.of()
        );

        assertThat(response.purchaseOrderNo()).isEqualTo("PO-001");
        assertThat(response.customerCode()).isNull();
        assertThat(response.projectId()).isNull();
    }
}
