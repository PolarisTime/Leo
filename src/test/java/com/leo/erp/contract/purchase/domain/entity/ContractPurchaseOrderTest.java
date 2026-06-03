package com.leo.erp.contract.purchase.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ContractPurchaseOrderTest {

    @Test
    void shouldCreateEntityWithDefaultValues() {
        var entity = new ContractPurchaseOrder();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getContract()).isNull();
        assertThat(entity.getPurchaseOrder()).isNull();
        assertThat(entity.getCreatedBy()).isNull();
        assertThat(entity.getCreatedName()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
    }

    @Test
    void shouldSetAndGetId() {
        var entity = new ContractPurchaseOrder();
        entity.setId(1L);
        assertThat(entity.getId()).isEqualTo(1L);
    }

    @Test
    void shouldSetAndGetContract() {
        var entity = new ContractPurchaseOrder();
        var contract = new PurchaseContract();
        contract.setId(100L);
        entity.setContract(contract);
        assertThat(entity.getContract()).isSameAs(contract);
        assertThat(entity.getContract().getId()).isEqualTo(100L);
    }

    @Test
    void shouldSetAndGetCreatedBy() {
        var entity = new ContractPurchaseOrder();
        entity.setCreatedBy(1L);
        assertThat(entity.getCreatedBy()).isEqualTo(1L);
    }

    @Test
    void shouldSetAndGetCreatedName() {
        var entity = new ContractPurchaseOrder();
        entity.setCreatedName("管理员");
        assertThat(entity.getCreatedName()).isEqualTo("管理员");
    }

    @Test
    void shouldSetAndGetCreatedAt() {
        var entity = new ContractPurchaseOrder();
        var now = LocalDateTime.now();
        entity.setCreatedAt(now);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
    }
}
