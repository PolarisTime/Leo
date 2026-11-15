package com.leo.erp.master.warehouse.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.support.OptionLimits;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.warehouse.service.WarehouseService;
import com.leo.erp.master.warehouse.web.dto.WarehouseOptionResponse;
import com.leo.erp.master.warehouse.web.dto.WarehouseRequest;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
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
@RequestMapping(ApiVersion.PREFIX + "/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @Operation(summary = "查询仓库下拉选项", description = "返回启用状态的仓库精简列表，供单据选择使用；数量受 OptionLimits 上限保护，不分页。需 WAREHOUSES_READ 权限。")
    @GetMapping("/options")
    @RequirePermission(PermissionCodes.WAREHOUSES_READ)
    public List<WarehouseOptionResponse> options() {
        return OptionLimits.cap(warehouseService.listActiveOptions());
    }

    @Operation(summary = "分页查询仓库", description = "按关键字、仓库类型与状态过滤，分页返回（PageResponse），默认按 warehouse 稳定排序。需 WAREHOUSES_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.WAREHOUSES_READ)
    public PageResponse<WarehouseResponse> page(@BindPageQuery(sortFieldKey = "warehouse") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String warehouseType, @RequestParam(required = false) String status) {
        return PageResponse.from(warehouseService.page(query, keyword, warehouseType, status));
    }

    @Operation(summary = "查询仓库详情", description = "按雪花 ID 查询单个仓库（路径 id 以字符串传递）；不存在返回 404。需 WAREHOUSES_READ 权限。")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.WAREHOUSES_READ)
    public WarehouseResponse detail(@PathVariable @SnowflakeId Long id) {
        return warehouseService.detail(id);
    }

    @Operation(summary = "创建仓库", description = "成功返回 201 与 Location /warehouses/{id}，响应体为新建仓库（id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 WAREHOUSES_CREATE 权限。")
    @PostMapping
    @Created
    @RequirePermission(PermissionCodes.WAREHOUSES_CREATE)
    public ResponseEntity<WarehouseResponse> create(@Valid @RequestBody WarehouseRequest request) {
        return ResponseSupport.created("/warehouses", warehouseService.create(request));
    }

    @Operation(summary = "更新仓库", description = "PUT 整体替换资源，成功返回 200；路径 id 为雪花 ID 字符串。重复提交需 Idempotency-Key（类级强制）。需 WAREHOUSES_UPDATE 权限。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.WAREHOUSES_UPDATE)
    public WarehouseResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    @Operation(summary = "删除仓库", description = "幂等删除，成功返回 204 No Content；重复删除结果一致。需 WAREHOUSES_DELETE 权限。")
    @DeleteMapping("/{id}")
    @NoContent
    @RequirePermission(PermissionCodes.WAREHOUSES_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        warehouseService.delete(id);
        return ResponseSupport.noContent();
    }
}
