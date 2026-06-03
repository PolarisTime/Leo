package com.leo.erp.sales.order.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderTest {

    @Test
    void shouldCreateSalesOrderWithDefaultValues() {
        SalesOrder order = new SalesOrder();

        assertThat(order.getItems()).isEmpty();
    }

    @Test
    void shouldSetAndGetAllFields() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-001");
        order.setPurchaseInboundNo("PI-001");
        order.setPurchaseOrderNo("PO-001");
        order.setCustomerName("客户A");
        order.setProjectName("项目A");
        order.setDeliveryDate(LocalDate.of(2026, 5, 1));
        order.setSalesName("张三");
        order.setTotalWeight(new BigDecimal("10.500"));
        order.setTotalAmount(new BigDecimal("31500.00"));
        order.setStatus("草稿");
        order.setCustomerCode("C001");
        order.setProjectId(100L);
        order.setRemark("备注");

        assertThat(order.getId()).isEqualTo(1L);
        assertThat(order.getOrderNo()).isEqualTo("SO-001");
        assertThat(order.getPurchaseInboundNo()).isEqualTo("PI-001");
        assertThat(order.getPurchaseOrderNo()).isEqualTo("PO-001");
        assertThat(order.getCustomerName()).isEqualTo("客户A");
        assertThat(order.getProjectName()).isEqualTo("项目A");
        assertThat(order.getDeliveryDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(order.getSalesName()).isEqualTo("张三");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("10.500");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("31500.00");
        assertThat(order.getStatus()).isEqualTo("草稿");
        assertThat(order.getCustomerCode()).isEqualTo("C001");
        assertThat(order.getProjectId()).isEqualTo(100L);
        assertThat(order.getRemark()).isEqualTo("备注");
    }

    @Test
    void shouldManageItemsList() {
        SalesOrder order = new SalesOrder();
        SalesOrderItem item = new SalesOrderItem();
        item.setId(1L);

        order.setItems(new ArrayList<>(List.of(item)));

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0)).isEqualTo(item);
    }
}
