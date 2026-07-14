package com.leo.erp.contract.sales.domain.entity;

import com.leo.erp.common.support.StatusConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesContractTest {

    @Test
    void shouldCreateEntityWithDefaultValues() {
        var entity = new SalesContract();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getContractNo()).isNull();
        assertThat(entity.getCustomerName()).isNull();
        assertThat(entity.getProjectName()).isNull();
        assertThat(entity.getSignDate()).isNull();
        assertThat(entity.getEffectiveDate()).isNull();
        assertThat(entity.getExpireDate()).isNull();
        assertThat(entity.getSalesName()).isNull();
        assertThat(entity.getTotalWeight()).isNull();
        assertThat(entity.getTotalAmount()).isNull();
        assertThat(entity.getStatus()).isNull();
        assertThat(entity.getRemark()).isNull();
        assertThat(entity.getItems()).isNotNull().isEmpty();
    }

    @Test
    void shouldSetAndGetId() {
        var entity = new SalesContract();
        entity.setId(1L);
        assertThat(entity.getId()).isEqualTo(1L);
    }

    @Test
    void shouldSetAndGetContractNo() {
        var entity = new SalesContract();
        entity.setContractNo("SC-001");
        assertThat(entity.getContractNo()).isEqualTo("SC-001");
    }

    @Test
    void shouldSetAndGetCustomerName() {
        var entity = new SalesContract();
        entity.setCustomerName("客户A");
        assertThat(entity.getCustomerName()).isEqualTo("客户A");
    }

    @Test
    void shouldSetAndGetProjectName() {
        var entity = new SalesContract();
        entity.setProjectName("项目A");
        assertThat(entity.getProjectName()).isEqualTo("项目A");
    }

    @Test
    void shouldSetAndGetSignDate() {
        var entity = new SalesContract();
        var date = LocalDate.now();
        entity.setSignDate(date);
        assertThat(entity.getSignDate()).isEqualTo(date);
    }

    @Test
    void shouldSetAndGetEffectiveDate() {
        var entity = new SalesContract();
        var date = LocalDate.now();
        entity.setEffectiveDate(date);
        assertThat(entity.getEffectiveDate()).isEqualTo(date);
    }

    @Test
    void shouldSetAndGetExpireDate() {
        var entity = new SalesContract();
        var date = LocalDate.now().plusYears(1);
        entity.setExpireDate(date);
        assertThat(entity.getExpireDate()).isEqualTo(date);
    }

    @Test
    void shouldSetAndGetSalesName() {
        var entity = new SalesContract();
        entity.setSalesName("销售甲");
        assertThat(entity.getSalesName()).isEqualTo("销售甲");
    }

    @Test
    void shouldSetAndGetTotalWeight() {
        var entity = new SalesContract();
        entity.setTotalWeight(new BigDecimal("100.000"));
        assertThat(entity.getTotalWeight()).isEqualByComparingTo(new BigDecimal("100.000"));
    }

    @Test
    void shouldSetAndGetTotalAmount() {
        var entity = new SalesContract();
        entity.setTotalAmount(new BigDecimal("50000.00"));
        assertThat(entity.getTotalAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
    }

    @Test
    void shouldSetAndGetStatus() {
        var entity = new SalesContract();
        entity.setStatus(StatusConstants.DRAFT);
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void shouldSetAndGetRemark() {
        var entity = new SalesContract();
        entity.setRemark("备注信息");
        assertThat(entity.getRemark()).isEqualTo("备注信息");
    }

    @Test
    void shouldSetAndGetItems() {
        var entity = new SalesContract();
        var items = new ArrayList<SalesContractItem>();
        entity.setItems(items);
        assertThat(entity.getItems()).isSameAs(items);
    }

    @Test
    void shouldSupportItemsManipulation() {
        var entity = new SalesContract();
        entity.setItems(new ArrayList<>());

        var item = new SalesContractItem();
        item.setId(1L);
        item.setMaterialCode("M001");
        entity.getItems().add(item);

        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getMaterialCode()).isEqualTo("M001");
    }
}
