package com.leo.erp.master.carrier.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.carrier.service.CarrierService;
import com.leo.erp.master.carrier.web.dto.CarrierOptionResponse;
import com.leo.erp.master.carrier.web.dto.CarrierRequest;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
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
@RequestMapping(ApiVersion.PREFIX + "/carriers")
public class CarrierController {

    private final CarrierService carrierService;

    public CarrierController(CarrierService carrierService) {
        this.carrierService = carrierService;
    }

    @Operation(summary = "查询物流商下拉选项", description = "返回启用状态的物流商精简列表（id、名称、简称等），供单据选择使用；不分页。需 CARRIERS_READ 权限。")
    @GetMapping("/options")
    @RequirePermission(PermissionCodes.CARRIERS_READ)
    public List<CarrierOptionResponse> options() {
        return carrierService.listActiveOptions();
    }

    @Operation(summary = "分页查询物流商", description = "按关键字与状态过滤，分页返回（PageResponse），默认按 carrier 稳定排序。需 CARRIERS_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.CARRIERS_READ)
    public PageResponse<CarrierResponse> page(@BindPageQuery(sortFieldKey = "carrier") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return PageResponse.from(carrierService.page(query, keyword, status));
    }

    @Operation(summary = "查询物流商详情", description = "按雪花 ID 查询单个物流商（路径 id 以字符串传递）；不存在返回 404。需 CARRIERS_READ 权限。")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.CARRIERS_READ)
    public CarrierResponse detail(@PathVariable @SnowflakeId Long id) {
        return carrierService.detail(id);
    }

    @Operation(summary = "创建物流商", description = "成功返回 201 与 Location /carriers/{id}，响应体为新建物流商（id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 CARRIERS_CREATE 权限。")
    @PostMapping
    @Created
    @RequirePermission(PermissionCodes.CARRIERS_CREATE)
    public ResponseEntity<CarrierResponse> create(@Valid @RequestBody CarrierRequest request) {
        return ResponseSupport.created("/carriers", carrierService.create(request));
    }

    @Operation(summary = "更新物流商", description = "PUT 整体替换资源，成功返回 200；路径 id 为雪花 ID 字符串。重复提交需 Idempotency-Key（类级强制）。需 CARRIERS_UPDATE 权限。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.CARRIERS_UPDATE)
    public CarrierResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody CarrierRequest request) {
        return carrierService.update(id, request);
    }

    @Operation(summary = "删除物流商", description = "幂等删除，成功返回 204 No Content；重复删除结果一致。需 CARRIERS_DELETE 权限。")
    @DeleteMapping("/{id}")
    @NoContent
    @RequirePermission(PermissionCodes.CARRIERS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        carrierService.delete(id);
        return ResponseSupport.noContent();
    }
}
