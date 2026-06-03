package com.leo.erp.contract.purchase.domain.entity;

import com.leo.erp.common.support.StatusConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseContractTest {

    @Test
    void shouldCreateEntityWithDefaultValues() {
        var entity = new PurchaseContract();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getContractNo()).isNull();
        assertThat(entity.getSupplierName()).isNull();
        assertThat(entity.getSignDate()).isNull();
        assertThat(entity.getEffectiveDate()).isNull();
        assertThat(entity.getExpireDate()).isNull();
        assertThat(entity.getBuyerName()).isNull();
        assertThat(entity.getTotalWeight()).isNull();
        assertThat(entity.getTotalAmount()).isNull();
        assertThat(entity.getStatus()).isNull();
        assertThat(entity.getRemark()).isNull();
        assertThat(entity.getItems()).isNotNull().isEmpty();
    }

    @Test
    void shouldSetAndGetId() {
        var entity = new PurchaseContract();
        entity.setId(1L);
        assertThat(entity.getId()).isEqualTo(1L);
    }

    @Test
    void shouldSetAndGetContractNo() {
        var entity = new PurchaseContract();
        entity.setContractNo("PC-001");
        assertThat(entity.getContractNo()).isEqualTo("PC-001");
    }

    @Test
    void shouldSetAndGetSupplierName() {
        var entity = new PurchaseContract();
        entity.setSupplierName("供应商A");
        assertThat(entity.getSupplierName()).isEqualTo("供应商A");
    }

    @Test
    void shouldSetAndGetSignDate() {
        var entity = new PurchaseContract();
        var date = LocalDate.now();
        entity.setSignDate(date);
        assertThat(entity.getSignDate()).isEqualTo(date);
    }

    @Test
    void shouldSetAndGetEffectiveDate() {
        var entity = new PurchaseContract();
        var date = LocalDate.now();
        entity.setEffectiveDate(date);
        assertThat(entity.getEffectiveDate()).isEqualTo(date);
    }

    @Test
    void shouldSetAndGetExpireDate() {
        var entity = new PurchaseContract();
        var date = LocalDate.now().plusYears(1);
        entity.setExpireDate(date);
        assertThat(entity.getExpireDate()).isEqualTo(date);
    }

    @Test
    void shouldSetAndGetBuyerName() {
        var entity = new PurchaseContract();
        entity.setBuyerName("采购甲");
        assertThat(entity.getBuyerName()).isEqualTo("采购甲");
    }

    @Test
    void shouldSetAndGetTotalWeight() {
        var entity = new PurchaseContract();
        entity.setTotalWeight(new BigDecimal("100.000"));
        assertThat(entity.getTotalWeight()).isEqualByComparingTo(new BigDecimal("100.000"));
    }

    @Test
    void shouldSetAndGetTotalAmount() {
        var entity = new PurchaseContract();
        entity.setTotalAmount(new BigDecimal("50000.00"));
        assertThat(entity.getTotalAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
    }

    @Test
    void shouldSetAndGetStatus() {
        var entity = new PurchaseContract();
        entity.setStatus(StatusConstants.DRAFT);
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void shouldSetAndGetRemark() {
        var entity = new PurchaseContract();
        entity.setRemark("备注信息");
        assertThat(entity.getRemark()).isEqualTo("备注信息");
    }

    @Test
    void shouldSetAndGetItems() {
        var entity = new PurchaseContract();
        var items = new ArrayList<PurchaseContractItem>();
        entity.setItems(items);
        assertThat(entity.getItems()).isSameAs(items);
    }

    @Test
    void shouldSupportItemsManipulation() {
        var entity = new PurchaseContract();
        entity.setItems(new ArrayList<>());

        var item = new PurchaseContractItem();
        item.setId(1L);
        item.setMaterialCode("M001");
        entity.getItems().add(item);

        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getMaterialCode()).isEqualTo("M001");
    }
}
