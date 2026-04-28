package com.leo.erp.sales.outbound.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.sales.outbound.service.SalesOutboundService;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sales-outbounds")
@Tag(name = "销售出库")
public class SalesOutboundController {

    private final SalesOutboundService service;

    public SalesOutboundController(SalesOutboundService service) {
        this.service = service;
    }

    @GetMapping("/search")
    @RequiresPermission(resource = "sales-outbound", action = "read")
    @Operation(summary = "搜索销售出库")
    public ApiResponse<java.util.List<SalesOutboundResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(service.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @GetMapping
    @RequiresPermission(resource = "sales-outbound", action = "read")
    @Operation(summary = "分页查询销售出库")
    public ApiResponse<PageResponse<SalesOutboundResponse>> page(
            @BindPageQuery(sortFieldKey = "sales-outbounds") PageQuery query,
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

    @GetMapping("/{id}")
    @RequiresPermission(resource = "sales-outbound", action = "read")
    @Operation(summary = "查询销售出库详情")
    public ApiResponse<SalesOutboundResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "sales-outbound", action = "create")
    @Operation(summary = "创建销售出库")
    public ApiResponse<SalesOutboundResponse> create(@Valid @RequestBody SalesOutboundRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "sales-outbound", action = "update")
    @Operation(summary = "更新销售出库")
    public ApiResponse<SalesOutboundResponse> update(@PathVariable Long id, @Valid @RequestBody SalesOutboundRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "sales-outbound", action = "delete")
    @Operation(summary = "删除销售出库")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
