package com.leo.erp.master.customer.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerResponseTest {

    @Test
    void recordAccessors() {
        CustomerResponse response = new CustomerResponse(
                1L, "C001", "客户A", "张三", "13800138000", "北京",
                "月结", "项目A", "PA", "北京市朝阳区", "启用", "备注"
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.customerCode()).isEqualTo("C001");
        assertThat(response.customerName()).isEqualTo("客户A");
        assertThat(response.contactName()).isEqualTo("张三");
        assertThat(response.contactPhone()).isEqualTo("13800138000");
        assertThat(response.city()).isEqualTo("北京");
        assertThat(response.settlementMode()).isEqualTo("月结");
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.projectNameAbbr()).isEqualTo("PA");
        assertThat(response.projectAddress()).isEqualTo("北京市朝阳区");
        assertThat(response.status()).isEqualTo("启用");
        assertThat(response.remark()).isEqualTo("备注");
    }

    @Test
    void recordEquality() {
        CustomerResponse a = new CustomerResponse(
                1L, "C001", "客户A", "张三", "13800138000", "北京",
                "月结", "项目A", "PA", "北京市朝阳区", "启用", "备注"
        );
        CustomerResponse b = new CustomerResponse(
                1L, "C001", "客户A", "张三", "13800138000", "北京",
                "月结", "项目A", "PA", "北京市朝阳区", "启用", "备注"
        );

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        CustomerResponse response = new CustomerResponse(
                1L, "C001", "客户A", "张三", "13800138000", "北京",
                "月结", "项目A", "PA", "北京市朝阳区", "启用", "备注"
        );
        assertThat(response.toString()).contains("C001", "客户A");
    }
}