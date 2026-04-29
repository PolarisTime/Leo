package com.leo.erp.master.customer.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.customer.service.CustomerService;
import com.leo.erp.master.customer.web.dto.CustomerRequest;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/options")
    @RequiresPermission(resource = "customer", action = "read")
    public ApiResponse<java.util.List<com.leo.erp.common.web.OptionResponse>> options() {
        return ApiResponse.success(customerService.listActiveOptions());
    }

    @GetMapping
    @RequiresPermission(resource = "customer", action = "read")
    public ApiResponse<PageResponse<CustomerResponse>> page(
            @BindPageQuery(sortFieldKey = "customers") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(customerService.page(query, keyword, status)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "customer", action = "read")
    public ApiResponse<CustomerResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(customerService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "customer", action = "create")
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return ApiResponse.success("创建成功", customerService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "customer", action = "update")
    public ApiResponse<CustomerResponse> update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return ApiResponse.success("更新成功", customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "customer", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
