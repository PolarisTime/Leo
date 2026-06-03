package com.leo.erp.sales.outbound.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOutboundTest {

    @Test
    void shouldCreateSalesOutboundWithDefaultValues() {
        SalesOutbound outbound = new SalesOutbound();

        assertThat(outbound.getItems()).isEmpty();
    }

    @Test
    void shouldSetAndGetAllFields() {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setOutboundNo("SOO-001");
        outbound.setSalesOrderNo("SO-001");
        outbound.setCustomerName("客户A");
        outbound.setProjectName("项目A");
        outbound.setWarehouseName("一号库");
        outbound.setOutboundDate(LocalDate.of(2026, 5, 1));
        outbound.setTotalWeight(new BigDecimal("10.500"));
        outbound.setTotalAmount(new BigDecimal("31500.00"));
        outbound.setStatus("草稿");
        outbound.setRemark("备注");

        assertThat(outbound.getId()).isEqualTo(1L);
        assertThat(outbound.getOutboundNo()).isEqualTo("SOO-001");
        assertThat(outbound.getSalesOrderNo()).isEqualTo("SO-001");
        assertThat(outbound.getCustomerName()).isEqualTo("客户A");
        assertThat(outbound.getProjectName()).isEqualTo("项目A");
        assertThat(outbound.getWarehouseName()).isEqualTo("一号库");
        assertThat(outbound.getOutboundDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(outbound.getTotalWeight()).isEqualByComparingTo("10.500");
        assertThat(outbound.getTotalAmount()).isEqualByComparingTo("31500.00");
        assertThat(outbound.getStatus()).isEqualTo("草稿");
        assertThat(outbound.getRemark()).isEqualTo("备注");
    }

    @Test
    void shouldManageItemsList() {
        SalesOutbound outbound = new SalesOutbound();
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(1L);

        outbound.setItems(new ArrayList<>(List.of(item)));

        assertThat(outbound.getItems()).hasSize(1);
        assertThat(outbound.getItems().get(0)).isEqualTo(item);
    }
}
