package com.leo.erp.master.customer.mapper;

import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerMapperTest {

    private final CustomerMapper mapper = new CustomerMapperImpl();

    @Test
    void shouldMapAllFieldsToResponse() {
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

        CustomerResponse response = mapper.toResponse(customer);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.customerCode()).isEqualTo("C001");
        assertThat(response.customerName()).isEqualTo("客户甲");
        assertThat(response.contactName()).isEqualTo("张三");
        assertThat(response.contactPhone()).isEqualTo("13800138000");
        assertThat(response.city()).isEqualTo("上海");
        assertThat(response.settlementMode()).isEqualTo("月结");
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.projectNameAbbr()).isEqualTo("XMA");
        assertThat(response.projectAddress()).isEqualTo("上海市浦东新区");
        assertThat(response.status()).isEqualTo("正常");
        assertThat(response.remark()).isEqualTo("测试备注");
    }

    @Test
    void shouldMapNullFieldsToNull() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCustomerCode("C001");

        CustomerResponse response = mapper.toResponse(customer);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.customerCode()).isEqualTo("C001");
        assertThat(response.contactName()).isNull();
        assertThat(response.city()).isNull();
    }
}
