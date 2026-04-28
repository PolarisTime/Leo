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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/purchase-inbounds")
@Tag(name = "采购入库")
public class PurchaseInboundController {

    private final PurchaseInboundService service;

    public PurchaseInboundController(PurchaseInboundService service) {
        this.service = service;
    }

    @GetMapping("/search")
    @RequiresPermission(resource = "purchase-inbound", action = "read")
    @Operation(summary = "搜索采购入库")
    public ApiResponse<java.util.List<PurchaseInboundResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(service.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @GetMapping
    @RequiresPermission(resource = "purchase-inbound", action = "read")
    @Operation(summary = "分页查询采购入库")
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
    @Operation(summary = "查询采购入库详情")
    public ApiResponse<PurchaseInboundResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "purchase-inbound", action = "create")
    @Operation(summary = "创建采购入库")
    public ApiResponse<PurchaseInboundResponse> create(@Valid @RequestBody PurchaseInboundRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "purchase-inbound", action = "update")
    @Operation(summary = "更新采购入库")
    public ApiResponse<PurchaseInboundResponse> update(@PathVariable Long id, @Valid @RequestBody PurchaseInboundRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "purchase-inbound", action = "delete")
    @Operation(summary = "删除采购入库")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
