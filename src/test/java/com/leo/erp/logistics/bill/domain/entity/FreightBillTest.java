package com.leo.erp.logistics.bill.domain.entity;

import com.leo.erp.common.support.StatusConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class FreightBillTest {

    @Test
    void shouldCreateEntityWithDefaultValues() {
        var entity = new FreightBill();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getBillNo()).isNull();
        assertThat(entity.getCarrierName()).isNull();
        assertThat(entity.getVehiclePlate()).isNull();
        assertThat(entity.getCustomerName()).isNull();
        assertThat(entity.getProjectName()).isNull();
        assertThat(entity.getBillTime()).isNull();
        assertThat(entity.getUnitPrice()).isNull();
        assertThat(entity.getTotalWeight()).isNull();
        assertThat(entity.getTotalFreight()).isNull();
        assertThat(entity.getStatus()).isNull();
        assertThat(entity.getDeliveryStatus()).isNull();
        assertThat(entity.getRemark()).isNull();
        assertThat(entity.getItems()).isNotNull().isEmpty();
    }

    @Test
    void shouldSetAndGetId() {
        var entity = new FreightBill();
        entity.setId(1L);
        assertThat(entity.getId()).isEqualTo(1L);
    }

    @Test
    void shouldSetAndGetBillNo() {
        var entity = new FreightBill();
        entity.setBillNo("FB-001");
        assertThat(entity.getBillNo()).isEqualTo("FB-001");
    }

    @Test
    void shouldSetAndGetCarrierName() {
        var entity = new FreightBill();
        entity.setCarrierName("物流公司A");
        assertThat(entity.getCarrierName()).isEqualTo("物流公司A");
    }

    @Test
    void shouldSetAndGetVehiclePlate() {
        var entity = new FreightBill();
        entity.setVehiclePlate("沪A12345");
        assertThat(entity.getVehiclePlate()).isEqualTo("沪A12345");
    }

    @Test
    void shouldSetAndGetCustomerName() {
        var entity = new FreightBill();
        entity.setCustomerName("客户A");
        assertThat(entity.getCustomerName()).isEqualTo("客户A");
    }

    @Test
    void shouldSetAndGetProjectName() {
        var entity = new FreightBill();
        entity.setProjectName("项目A");
        assertThat(entity.getProjectName()).isEqualTo("项目A");
    }

    @Test
    void shouldSetAndGetBillTime() {
        var entity = new FreightBill();
        var date = LocalDate.now();
        entity.setBillTime(date);
        assertThat(entity.getBillTime()).isEqualTo(date);
    }

    @Test
    void shouldSetAndGetUnitPrice() {
        var entity = new FreightBill();
        entity.setUnitPrice(new BigDecimal("50.00"));
        assertThat(entity.getUnitPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void shouldSetAndGetTotalWeight() {
        var entity = new FreightBill();
        entity.setTotalWeight(new BigDecimal("100.000"));
        assertThat(entity.getTotalWeight()).isEqualByComparingTo(new BigDecimal("100.000"));
    }

    @Test
    void shouldSetAndGetTotalFreight() {
        var entity = new FreightBill();
        entity.setTotalFreight(new BigDecimal("5000.00"));
        assertThat(entity.getTotalFreight()).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    void shouldSetAndGetStatus() {
        var entity = new FreightBill();
        entity.setStatus(StatusConstants.DRAFT);
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void shouldSetAndGetDeliveryStatus() {
        var entity = new FreightBill();
        entity.setDeliveryStatus("PENDING");
        assertThat(entity.getDeliveryStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldSetAndGetRemark() {
        var entity = new FreightBill();
        entity.setRemark("备注信息");
        assertThat(entity.getRemark()).isEqualTo("备注信息");
    }

    @Test
    void shouldSetAndGetItems() {
        var entity = new FreightBill();
        var items = new ArrayList<FreightBillItem>();
        entity.setItems(items);
        assertThat(entity.getItems()).isSameAs(items);
    }

    @Test
    void shouldSupportItemsManipulation() {
        var entity = new FreightBill();
        entity.setItems(new ArrayList<>());

        var item = new FreightBillItem();
        item.setId(1L);
        item.setMaterialCode("M001");
        entity.getItems().add(item);

        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getMaterialCode()).isEqualTo("M001");
    }
}
