package com.leo.erp.master.customer.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.customer.service.CustomerService;
import com.leo.erp.master.customer.web.dto.CustomerOptionResponse;
import com.leo.erp.master.customer.web.dto.CustomerRequest;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.api.ResponseSupport;
import com.leo.erp.common.api.Created;
import com.leo.erp.common.api.NoContent;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Operation(summary = "查询客户下拉选项", description = "返回启用状态的客户精简列表，供单据选择使用；不分页。需 CUSTOMERS_READ 权限。")
    @GetMapping("/options")
    @RequirePermission(PermissionCodes.CUSTOMERS_READ)
    public List<CustomerOptionResponse> options() {
        return customerService.listActiveOptions();
    }

    @Operation(summary = "分页查询客户", description = "按关键字与状态过滤，分页返回（PageResponse），默认按 customer 稳定排序。需 CUSTOMERS_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.CUSTOMERS_READ)
    public PageResponse<CustomerResponse> page(@BindPageQuery(sortFieldKey = "customer") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return PageResponse.from(customerService.page(query, keyword, status));
    }

    @Operation(summary = "查询客户详情", description = "按雪花 ID 查询单个客户（路径 id 以字符串传递）；不存在返回 404。需 CUSTOMERS_READ 权限。")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.CUSTOMERS_READ)
    public CustomerResponse detail(@PathVariable @SnowflakeId Long id) {
        return customerService.detail(id);
    }

    @Operation(summary = "创建客户", description = "成功返回 201 与 Location /customers/{id}，响应体为新建客户（id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 CUSTOMERS_CREATE 权限。")
    @PostMapping
    @Created
    @RequirePermission(PermissionCodes.CUSTOMERS_CREATE)
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseSupport.created("/customers", customerService.create(request));
    }

    @Operation(summary = "更新客户", description = "PUT 整体替换资源，成功返回 200；路径 id 为雪花 ID 字符串。重复提交需 Idempotency-Key（类级强制）。需 CUSTOMERS_UPDATE 权限。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.CUSTOMERS_UPDATE)
    public CustomerResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @Operation(summary = "删除客户", description = "幂等删除，成功返回 204 No Content；重复删除结果一致。需 CUSTOMERS_DELETE 权限。")
    @DeleteMapping("/{id}")
    @NoContent
    @RequirePermission(PermissionCodes.CUSTOMERS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        customerService.delete(id);
        return ResponseSupport.noContent();
    }
}
