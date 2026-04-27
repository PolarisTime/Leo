package com.leo.erp.purchase.inbound.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.purchase.inbound.service.PurchaseInboundService;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/purchase-inbounds")
public class PurchaseInboundController {

    private final PurchaseInboundService service;

    public PurchaseInboundController(PurchaseInboundService service) {
        this.service = service;
    }

    @GetMapping("/search")
    @RequiresPermission(resource = "purchase-inbound", action = "read")
    public ApiResponse<java.util.List<PurchaseInboundResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(service.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @GetMapping
    @RequiresPermission(resource = "purchase-inbound", action = "read")
    public ApiResponse<PageResponse<PurchaseInboundResponse>> page(
            @BindPageQuery(sortFieldKey = "purchase-inbounds") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                service.page(query, keyword, supplierName, status, startDate, endDate)
        ));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "purchase-inbound", action = "read")
    public ApiResponse<PurchaseInboundResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "purchase-inbound", action = "create")
    public ApiResponse<PurchaseInboundResponse> create(@Valid @RequestBody PurchaseInboundRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "purchase-inbound", action = "update")
    public ApiResponse<PurchaseInboundResponse> update(@PathVariable Long id, @Valid @RequestBody PurchaseInboundRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "purchase-inbound", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
