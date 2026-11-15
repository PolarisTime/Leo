package com.leo.erp.master.valuealias.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.Created;
import com.leo.erp.common.api.NoContent;
import com.leo.erp.common.api.ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.valuealias.service.ValueAliasService;
import com.leo.erp.master.valuealias.web.dto.ValueAliasRequest;
import com.leo.erp.master.valuealias.web.dto.ValueAliasResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
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

/**
 * 值映射/别名维护接口。
 *
 * <p>资源路径 {@code /api/v2.0/value-aliases} 与权限码资源名 {@code value-aliases} 对齐。</p>
 */
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/value-aliases")
public class ValueAliasController {

    private final ValueAliasService valueAliasService;

    public ValueAliasController(ValueAliasService valueAliasService) {
        this.valueAliasService = valueAliasService;
    }

    @Operation(summary = "分页查询值映射别名", description = "按维度与关键字过滤，分页返回（PageResponse），默认按 value-alias 稳定排序。需 VALUE_ALIASES_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.VALUE_ALIASES_READ)
    public PageResponse<ValueAliasResponse> page(
            @BindPageQuery(sortFieldKey = "value-alias") PageQuery query,
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) String keyword) {
        return PageResponse.from(valueAliasService.page(query, dimension, keyword));
    }

    @Operation(summary = "查询值映射别名详情", description = "按雪花 ID 查询单条别名（路径 id 以字符串传递）；不存在返回 404。需 VALUE_ALIASES_READ 权限。")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.VALUE_ALIASES_READ)
    public ValueAliasResponse detail(@PathVariable @SnowflakeId Long id) {
        return valueAliasService.detail(id);
    }

    @Operation(summary = "创建值映射别名", description = "成功返回 201 与 Location /value-aliases/{id}，响应体为新建别名（id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 VALUE_ALIASES_CREATE 权限。")
    @PostMapping
    @Created
    @RequirePermission(PermissionCodes.VALUE_ALIASES_CREATE)
    public ResponseEntity<ValueAliasResponse> create(@Valid @RequestBody ValueAliasRequest request) {
        return ResponseSupport.created("/value-aliases", valueAliasService.create(request));
    }

    @Operation(summary = "更新值映射别名", description = "PUT 整体替换资源，成功返回 200；路径 id 为雪花 ID 字符串。重复提交需 Idempotency-Key（类级强制）。需 VALUE_ALIASES_UPDATE 权限。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.VALUE_ALIASES_UPDATE)
    public ValueAliasResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody ValueAliasRequest request) {
        return valueAliasService.update(id, request);
    }

    @Operation(summary = "删除值映射别名", description = "幂等删除，成功返回 204 No Content；重复删除结果一致。需 VALUE_ALIASES_DELETE 权限。")
    @DeleteMapping("/{id}")
    @NoContent
    @RequirePermission(PermissionCodes.VALUE_ALIASES_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        valueAliasService.delete(id);
        return ResponseSupport.noContent();
    }
}
