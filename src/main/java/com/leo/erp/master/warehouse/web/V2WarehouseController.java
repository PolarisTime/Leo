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
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/warehouses")
public class V2WarehouseController {

    private final WarehouseService warehouseService;

    public V2WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping("/options")
    @RequirePermission(PermissionCodes.WAREHOUSES_READ)
    public List<WarehouseOptionResponse> options() {
        return OptionLimits.cap(warehouseService.listActiveOptions());
    }

    @GetMapping
    @RequirePermission(PermissionCodes.WAREHOUSES_READ)
    public PageResponse<WarehouseResponse> page(@BindPageQuery(sortFieldKey = "warehouse") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String warehouseType, @RequestParam(required = false) String status) {
        return PageResponse.from(warehouseService.page(query, keyword, warehouseType, status));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.WAREHOUSES_READ)
    public WarehouseResponse detail(@PathVariable Long id) {
        return warehouseService.detail(id);
    }

    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.WAREHOUSES_CREATE)
    public ResponseEntity<WarehouseResponse> create(@Valid @RequestBody WarehouseRequest request) {
        return V2ResponseSupport.created("/warehouses", warehouseService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.WAREHOUSES_UPDATE)
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.WAREHOUSES_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
