package com.leo.erp.master.warehouse.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.warehouse.service.WarehouseService;
import com.leo.erp.master.warehouse.web.dto.WarehouseRequest;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping("/options")
    @RequiresPermission(resource = "warehouse", action = "read")
    public ApiResponse<java.util.List<com.leo.erp.common.web.OptionResponse>> options() {
        return ApiResponse.success(warehouseService.listActiveOptions());
    }

    @GetMapping
    @RequiresPermission(resource = "warehouse", action = "read")
    public ApiResponse<PageResponse<WarehouseResponse>> page(
            @BindPageQuery(sortFieldKey = "warehouses") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String warehouseType,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(warehouseService.page(query, keyword, warehouseType, status)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "warehouse", action = "read")
    public ApiResponse<WarehouseResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(warehouseService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "warehouse", action = "create")
    public ApiResponse<WarehouseResponse> create(@Valid @RequestBody WarehouseRequest request) {
        return ApiResponse.success("创建成功", warehouseService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "warehouse", action = "update")
    public ApiResponse<WarehouseResponse> update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return ApiResponse.success("更新成功", warehouseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "warehouse", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
