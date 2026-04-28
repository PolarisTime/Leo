package com.leo.erp.sales.order.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "销售订单")
@RestController
@RequestMapping("/sales-orders")
public class SalesOrderController {

    private final SalesOrderService service;

    public SalesOrderController(SalesOrderService service) {
        this.service = service;
    }

    @Operation(summary = "搜索销售订单")
    @GetMapping("/search")
    @RequiresPermission(resource = "sales-order", action = "read")
    public ApiResponse<java.util.List<SalesOrderResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(service.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @Operation(summary = "分页查询销售订单")
    @GetMapping
    @RequiresPermission(resource = "sales-order", action = "read")
    public ApiResponse<PageResponse<SalesOrderResponse>> page(
            @BindPageQuery(sortFieldKey = "sales-orders") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                service.page(query, keyword, customerName, status, startDate, endDate)
        ));
    }

    @Operation(summary = "查询销售订单详情")
    @GetMapping("/{id}")
    @RequiresPermission(resource = "sales-order", action = "read")
    public ApiResponse<SalesOrderResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @Operation(summary = "创建销售订单")
    @PostMapping
    @RequiresPermission(resource = "sales-order", action = "create")
    public ApiResponse<SalesOrderResponse> create(@Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @Operation(summary = "更新销售订单")
    @PutMapping("/{id}")
    @RequiresPermission(resource = "sales-order", action = "update")
    public ApiResponse<SalesOrderResponse> update(@PathVariable Long id, @Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @Operation(summary = "删除销售订单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "sales-order", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
