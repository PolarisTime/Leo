package com.leo.erp.market.lengthpremium.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.Created;
import com.leo.erp.common.api.NoContent;
import com.leo.erp.common.api.ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.market.lengthpremium.service.LengthPremiumService;
import com.leo.erp.market.lengthpremium.web.dto.LengthPremiumRequest;
import com.leo.erp.market.lengthpremium.web.dto.LengthPremiumResponse;
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
 * 定尺加价维护接口。资源路径与权限码资源名 {@code length-premiums} 对齐。
 */
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/length-premiums")
public class LengthPremiumController {

    private final LengthPremiumService lengthPremiumService;

    public LengthPremiumController(LengthPremiumService lengthPremiumService) {
        this.lengthPremiumService = lengthPremiumService;
    }

    @Operation(summary = "分页查询定尺加价", description = "按关键字与状态过滤，分页返回（PageResponse）；未显式指定方向时默认升序，默认按 length-premium 排序。需 LENGTH_PREMIUMS_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.LENGTH_PREMIUMS_READ)
    public PageResponse<LengthPremiumResponse> page(
            @BindPageQuery(sortFieldKey = "length-premium") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        // 与取价地区同理: PageQuery 默认 DESC, 但排序号语义要求默认升序
        PageQuery effective = (query.direction() == null || query.direction().isBlank())
                ? new PageQuery(query.page(), query.size(), query.sortBy(), "asc")
                : query;
        return PageResponse.from(lengthPremiumService.page(effective, keyword, status));
    }

    @Operation(summary = "查询定尺加价详情", description = "按雪花 ID 查询单条定尺加价规则（路径 id 以字符串传递）；不存在返回 404。需 LENGTH_PREMIUMS_READ 权限。")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.LENGTH_PREMIUMS_READ)
    public LengthPremiumResponse detail(@PathVariable @SnowflakeId Long id) {
        return lengthPremiumService.detail(id);
    }

    @Operation(summary = "创建定尺加价", description = "成功返回 201 与 Location /length-premiums/{id}，响应体为新建规则（id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 LENGTH_PREMIUMS_CREATE 权限。")
    @PostMapping
    @Created
    @RequirePermission(PermissionCodes.LENGTH_PREMIUMS_CREATE)
    public ResponseEntity<LengthPremiumResponse> create(@Valid @RequestBody LengthPremiumRequest request) {
        return ResponseSupport.created("/length-premiums", lengthPremiumService.create(request));
    }

    @Operation(summary = "更新定尺加价", description = "PUT 整体替换资源，成功返回 200；路径 id 为雪花 ID 字符串。重复提交需 Idempotency-Key（类级强制）。需 LENGTH_PREMIUMS_UPDATE 权限。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.LENGTH_PREMIUMS_UPDATE)
    public LengthPremiumResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody LengthPremiumRequest request) {
        return lengthPremiumService.update(id, request);
    }

    @Operation(summary = "删除定尺加价", description = "幂等删除，成功返回 204 No Content；重复删除结果一致。需 LENGTH_PREMIUMS_DELETE 权限。")
    @DeleteMapping("/{id}")
    @NoContent
    @RequirePermission(PermissionCodes.LENGTH_PREMIUMS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        lengthPremiumService.delete(id);
        return ResponseSupport.noContent();
    }
}
