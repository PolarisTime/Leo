package com.leo.erp.master.material.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.support.OptionLimits;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.material.service.MaterialCategoryService;
import com.leo.erp.master.material.web.dto.MaterialCategoryOptionResponse;
import com.leo.erp.master.material.web.dto.MaterialCategoryRequest;
import com.leo.erp.master.material.web.dto.MaterialCategoryResponse;
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
@RequestMapping(ApiVersion.PREFIX + "/material-categories")
public class MaterialCategoryController {

    private final MaterialCategoryService service;

    public MaterialCategoryController(MaterialCategoryService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询商品分类", description = "按关键字与状态过滤，分页返回（PageResponse），默认按 material-category 稳定排序。需 MATERIAL_CATEGORIES_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.MATERIAL_CATEGORIES_READ)
    public PageResponse<MaterialCategoryResponse> page(@BindPageQuery(sortFieldKey = "material-category") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return PageResponse.from(service.page(query, keyword, status));
    }

    @Operation(summary = "查询商品分类详情", description = "按雪花 ID 查询单个商品分类（路径 id 以字符串传递）；不存在返回 404。需 MATERIAL_CATEGORIES_READ 权限。")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.MATERIAL_CATEGORIES_READ)
    public MaterialCategoryResponse detail(@PathVariable @SnowflakeId Long id) {
        return service.detail(id);
    }

    @Operation(summary = "创建商品分类", description = "成功返回 201 与 Location /material-categories/{id}，响应体为新建分类（id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 MATERIAL_CATEGORIES_CREATE 权限。")
    @PostMapping
    @Created
    @RequirePermission(PermissionCodes.MATERIAL_CATEGORIES_CREATE)
    public ResponseEntity<MaterialCategoryResponse> create(@Valid @RequestBody MaterialCategoryRequest request) {
        return ResponseSupport.created("/material-categories", service.create(request));
    }

    @Operation(summary = "更新商品分类", description = "PUT 整体替换资源，成功返回 200；路径 id 为雪花 ID 字符串。重复提交需 Idempotency-Key（类级强制）。需 MATERIAL_CATEGORIES_UPDATE 权限。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.MATERIAL_CATEGORIES_UPDATE)
    public MaterialCategoryResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody MaterialCategoryRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "删除商品分类", description = "幂等删除，成功返回 204 No Content；重复删除结果一致。需 MATERIAL_CATEGORIES_DELETE 权限。")
    @DeleteMapping("/{id}")
    @NoContent
    @RequirePermission(PermissionCodes.MATERIAL_CATEGORIES_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        service.delete(id);
        return ResponseSupport.noContent();
    }

    @Operation(summary = "查询商品分类下拉选项", description = "返回启用状态的商品分类精简列表，供商品资料与单据选择；数量受 OptionLimits 上限保护，不分页。需 MATERIAL_CATEGORIES_READ 权限。")
    @GetMapping("/options")
    @RequirePermission(PermissionCodes.MATERIAL_CATEGORIES_READ)
    public List<MaterialCategoryOptionResponse> options() {
        return OptionLimits.cap(service.options());
    }
}
