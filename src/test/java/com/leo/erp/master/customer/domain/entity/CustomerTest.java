package com.leo.erp.master.customer.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerTest {

    @Test
    void shouldSetAndGetAllFields() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCustomerCode("C001");
        customer.setCustomerName("客户甲");
        customer.setContactName("张三");
        customer.setContactPhone("13800138000");
        customer.setCity("上海");
        customer.setSettlementMode("月结");
        customer.setProjectName("项目A");
        customer.setProjectNameAbbr("XMA");
        customer.setProjectAddress("上海市浦东新区");
        customer.setStatus("正常");
        customer.setRemark("测试备注");

        assertThat(customer.getId()).isEqualTo(1L);
        assertThat(customer.getCustomerCode()).isEqualTo("C001");
        assertThat(customer.getCustomerName()).isEqualTo("客户甲");
        assertThat(customer.getContactName()).isEqualTo("张三");
        assertThat(customer.getContactPhone()).isEqualTo("13800138000");
        assertThat(customer.getCity()).isEqualTo("上海");
        assertThat(customer.getSettlementMode()).isEqualTo("月结");
        assertThat(customer.getProjectName()).isEqualTo("项目A");
        assertThat(customer.getProjectNameAbbr()).isEqualTo("XMA");
        assertThat(customer.getProjectAddress()).isEqualTo("上海市浦东新区");
        assertThat(customer.getStatus()).isEqualTo("正常");
        assertThat(customer.getRemark()).isEqualTo("测试备注");
    }

    @Test
    void shouldReturnNullForUnsetFields() {
        Customer customer = new Customer();
        assertThat(customer.getId()).isNull();
        assertThat(customer.getCustomerCode()).isNull();
        assertThat(customer.getCity()).isNull();
        assertThat(customer.getSettlementMode()).isNull();
    }
}
