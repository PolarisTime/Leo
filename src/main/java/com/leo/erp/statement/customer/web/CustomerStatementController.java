package com.leo.erp.statement.customer.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.statement.customer.service.CustomerStatementService;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "客户对账单")
@RestController
@RequestMapping("/customer-statements")
public class CustomerStatementController {

    private final CustomerStatementService customerStatementService;

    public CustomerStatementController(CustomerStatementService customerStatementService) {
        this.customerStatementService = customerStatementService;
    }

    @Operation(summary = "分页查询客户对账单")
    @GetMapping
    @RequiresPermission(resource = "customer-statement", action = "read")
    public ApiResponse<PageResponse<CustomerStatementResponse>> page(
            @BindPageQuery(sortFieldKey = "customer-statements") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd
    ) {
        return ApiResponse.success(PageResponse.from(
                customerStatementService.page(
                        query,
                        keyword,
                        customerName,
                        status,
                        periodStart,
                        periodEnd
                )
        ));
    }

    @Operation(summary = "查询客户对账单详情")
    @GetMapping("/{id}")
    @RequiresPermission(resource = "customer-statement", action = "read")
    public ApiResponse<CustomerStatementResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(customerStatementService.detail(id));
    }

    @Operation(summary = "创建客户对账单")
    @PostMapping
    @RequiresPermission(resource = "customer-statement", action = "create")
    public ApiResponse<CustomerStatementResponse> create(@Valid @RequestBody CustomerStatementRequest request) {
        return ApiResponse.success("创建成功", customerStatementService.create(request));
    }

    @Operation(summary = "更新客户对账单")
    @PutMapping("/{id}")
    @RequiresPermission(resource = "customer-statement", action = "update")
    public ApiResponse<CustomerStatementResponse> update(@PathVariable Long id, @Valid @RequestBody CustomerStatementRequest request) {
        return ApiResponse.success("更新成功", customerStatementService.update(id, request));
    }

    @Operation(summary = "删除客户对账单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "customer-statement", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerStatementService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
