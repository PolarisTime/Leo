package com.leo.erp.market.region.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.Created;
import com.leo.erp.common.api.NoContent;
import com.leo.erp.common.api.ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.market.region.service.QuoteRegionService;
import com.leo.erp.market.region.web.dto.QuoteRegionRequest;
import com.leo.erp.market.region.web.dto.QuoteRegionResponse;
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
 * 取价地区维护接口。资源路径与权限码资源名 {@code quote-regions} 对齐。
 */
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/quote-regions")
public class QuoteRegionController {

    private final QuoteRegionService quoteRegionService;

    public QuoteRegionController(QuoteRegionService quoteRegionService) {
        this.quoteRegionService = quoteRegionService;
    }

    @Operation(summary = "分页查询取价地区", description = "按关键字与状态过滤，分页返回（PageResponse）；未显式指定方向时默认升序（与运行时配置下发的地区顺序一致），默认按 quote-region 排序。需 QUOTE_REGIONS_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.QUOTE_REGIONS_READ)
    public PageResponse<QuoteRegionResponse> page(
            @BindPageQuery(sortFieldKey = "quote-region") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        // PageQuery 默认方向是 DESC(适合"最新在前"的单据类列表), 但本资源的排序号语义是
        // "按抓取顺序", 因此未显式指定方向时改用升序, 与运行时配置下发的地区顺序保持一致。
        PageQuery effective = (query.direction() == null || query.direction().isBlank())
                ? new PageQuery(query.page(), query.size(), query.sortBy(), "asc")
                : query;
        return PageResponse.from(quoteRegionService.page(effective, keyword, status));
    }

    @Operation(summary = "查询取价地区详情", description = "按雪花 ID 查询单个取价地区（路径 id 以字符串传递）；不存在返回 404。需 QUOTE_REGIONS_READ 权限。")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.QUOTE_REGIONS_READ)
    public QuoteRegionResponse detail(@PathVariable @SnowflakeId Long id) {
        return quoteRegionService.detail(id);
    }

    @Operation(summary = "创建取价地区", description = "成功返回 201 与 Location /quote-regions/{id}，响应体为新建地区（id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 QUOTE_REGIONS_CREATE 权限。")
    @PostMapping
    @Created
    @RequirePermission(PermissionCodes.QUOTE_REGIONS_CREATE)
    public ResponseEntity<QuoteRegionResponse> create(@Valid @RequestBody QuoteRegionRequest request) {
        return ResponseSupport.created("/quote-regions", quoteRegionService.create(request));
    }

    @Operation(summary = "更新取价地区", description = "PUT 整体替换资源，成功返回 200；路径 id 为雪花 ID 字符串。重复提交需 Idempotency-Key（类级强制）。需 QUOTE_REGIONS_UPDATE 权限。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.QUOTE_REGIONS_UPDATE)
    public QuoteRegionResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody QuoteRegionRequest request) {
        return quoteRegionService.update(id, request);
    }

    @Operation(summary = "删除取价地区", description = "幂等删除，成功返回 204 No Content；重复删除结果一致。需 QUOTE_REGIONS_DELETE 权限。")
    @DeleteMapping("/{id}")
    @NoContent
    @RequirePermission(PermissionCodes.QUOTE_REGIONS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        quoteRegionService.delete(id);
        return ResponseSupport.noContent();
    }
}
