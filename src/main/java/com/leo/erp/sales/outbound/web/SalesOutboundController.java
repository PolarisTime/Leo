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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sales-outbounds")
public class SalesOutboundController {

    private final SalesOutboundService service;

    public SalesOutboundController(SalesOutboundService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission(resource = "sales-outbound", action = "read")
    public ApiResponse<PageResponse<SalesOutboundResponse>> page(
            @BindPageQuery(sortFieldKey = "sales-outbounds") PageQuery query,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(PageResponse.from(service.page(query, keyword)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "sales-outbound", action = "read")
    public ApiResponse<SalesOutboundResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "sales-outbound", action = "create")
    public ApiResponse<SalesOutboundResponse> create(@Valid @RequestBody SalesOutboundRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "sales-outbound", action = "update")
    public ApiResponse<SalesOutboundResponse> update(@PathVariable Long id, @Valid @RequestBody SalesOutboundRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "sales-outbound", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
