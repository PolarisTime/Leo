package com.leo.erp.master.supplier.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.supplier.service.SupplierService;
import com.leo.erp.master.supplier.web.dto.SupplierRequest;
import com.leo.erp.master.supplier.web.dto.SupplierOptionResponse;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
@RequestMapping(ApiVersion.PREFIX + "/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @Operation(summary = "查询供应商下拉选项", description = "返回启用状态的供应商精简列表，供单据选择使用；不分页。需 SUPPLIERS_READ 权限。")
    @GetMapping("/options")
    @RequirePermission(PermissionCodes.SUPPLIERS_READ)
    public java.util.List<SupplierOptionResponse> options() {
        return supplierService.listActiveOptions();
    }

    @Operation(summary = "分页查询供应商", description = "按关键字与状态过滤，分页返回（PageResponse），默认按 supplier 稳定排序。需 SUPPLIERS_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.SUPPLIERS_READ)
    public PageResponse<SupplierResponse> page(@BindPageQuery(sortFieldKey = "supplier") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return PageResponse.from(supplierService.page(query, keyword, status));
    }

    @Operation(summary = "查询供应商详情", description = "按雪花 ID 查询单个供应商（路径 id 以字符串传递）；不存在返回 404。需 SUPPLIERS_READ 权限。")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.SUPPLIERS_READ)
    public SupplierResponse detail(@PathVariable @SnowflakeId Long id) {
        return supplierService.detail(id);
    }

    @Operation(summary = "创建供应商", description = "成功返回 201 与 Location /suppliers/{id}，响应体为新建供应商（id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 SUPPLIERS_CREATE 权限。")
    @PostMapping
    @Created
    @RequirePermission(PermissionCodes.SUPPLIERS_CREATE)
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        return ResponseSupport.created("/suppliers", supplierService.create(request));
    }

    @Operation(summary = "更新供应商", description = "PUT 整体替换资源，成功返回 200；路径 id 为雪花 ID 字符串。重复提交需 Idempotency-Key（类级强制）。需 SUPPLIERS_UPDATE 权限。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.SUPPLIERS_UPDATE)
    public SupplierResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @Operation(summary = "删除供应商", description = "幂等删除，成功返回 204 No Content；重复删除结果一致。需 SUPPLIERS_DELETE 权限。")
    @DeleteMapping("/{id}")
    @NoContent
    @RequirePermission(PermissionCodes.SUPPLIERS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        supplierService.delete(id);
        return ResponseSupport.noContent();
    }
}
