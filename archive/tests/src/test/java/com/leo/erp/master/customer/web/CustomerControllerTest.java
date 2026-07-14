package com.leo.erp.master.customer.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.master.customer.service.CustomerService;
import com.leo.erp.master.customer.web.dto.CustomerOptionResponse;
import com.leo.erp.master.customer.web.dto.CustomerRequest;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerControllerTest {

    private final CustomerService customerService = mock(CustomerService.class);
    private final CustomerController controller = new CustomerController(customerService);

    @Test
    void optionsReturnsActiveCustomers() {
        CustomerOptionResponse option = new CustomerOptionResponse(
                1L, "客户甲 / 项目A", "客户甲", "C001", "客户甲", "项目A", "XMA"
        );
        when(customerService.listActiveOptions()).thenReturn(List.of(option));

        ApiResponse<List<CustomerOptionResponse>> response = controller.options();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(option);
        verify(customerService).listActiveOptions();
    }

    @Test
    void pageReturnsPaginatedCustomers() {
        CustomerResponse customer = mock(CustomerResponse.class);
        Page<CustomerResponse> page = new PageImpl<>(List.of(customer));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(customerService.page(any(), eq("test"), eq("active"))).thenReturn(page);

        ApiResponse<PageResponse<CustomerResponse>> response = controller.page(query, "test", "active");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsCustomerById() {
        CustomerResponse customer = mock(CustomerResponse.class);
        when(customerService.detail(1L)).thenReturn(customer);

        ApiResponse<CustomerResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(customer);
    }

    @Test
    void createReturnsCreatedCustomer() {
        CustomerRequest request = mock(CustomerRequest.class);
        CustomerResponse created = mock(CustomerResponse.class);
        when(customerService.create(request)).thenReturn(created);

        ApiResponse<CustomerResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(customerService).create(request);
    }

    @Test
    void updateReturnsUpdatedCustomer() {
        CustomerRequest request = mock(CustomerRequest.class);
        CustomerResponse updated = mock(CustomerResponse.class);
        when(customerService.update(1L, request)).thenReturn(updated);

        ApiResponse<CustomerResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(customerService).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(customerService).delete(1L);
    }
}