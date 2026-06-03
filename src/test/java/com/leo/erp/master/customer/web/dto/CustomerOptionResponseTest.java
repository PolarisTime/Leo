package com.leo.erp.master.customer.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerOptionResponseTest {

    @Test
    void shouldCreateRecord() {
        CustomerOptionResponse response = new CustomerOptionResponse(
                1L, "客户A", "客户A", "C001", "客户A", "项目X", "项X"
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.label()).isEqualTo("客户A");
        assertThat(response.value()).isEqualTo("客户A");
        assertThat(response.customerCode()).isEqualTo("C001");
        assertThat(response.customerName()).isEqualTo("客户A");
        assertThat(response.projectName()).isEqualTo("项目X");
        assertThat(response.projectNameAbbr()).isEqualTo("项X");
    }

    @Test
    void shouldSupportNullValues() {
        CustomerOptionResponse response = new CustomerOptionResponse(
                null, null, null, null, null, null, null
        );

        assertThat(response.id()).isNull();
        assertThat(response.label()).isNull();
        assertThat(response.value()).isNull();
        assertThat(response.customerCode()).isNull();
        assertThat(response.customerName()).isNull();
        assertThat(response.projectName()).isNull();
        assertThat(response.projectNameAbbr()).isNull();
    }

    @Test
    void shouldImplementEquals() {
        CustomerOptionResponse r1 = new CustomerOptionResponse(
                1L, "客户A", "客户A", "C001", "客户A", "项目X", "项X"
        );
        CustomerOptionResponse r2 = new CustomerOptionResponse(
                1L, "客户A", "客户A", "C001", "客户A", "项目X", "项X"
        );

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void shouldImplementHashCode() {
        CustomerOptionResponse r1 = new CustomerOptionResponse(
                1L, "客户A", "客户A", "C001", "客户A", "项目X", "项X"
        );
        CustomerOptionResponse r2 = new CustomerOptionResponse(
                1L, "客户A", "客户A", "C001", "客户A", "项目X", "项X"
        );

        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }
}
