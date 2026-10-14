package com.leo.erp.market.region.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.market.region.service.QuoteRegionService;
import com.leo.erp.market.region.web.dto.QuoteRegionRequest;
import com.leo.erp.market.region.web.dto.QuoteRegionResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import jakarta.validation.Valid;
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
@RequestMapping(ApiVersion.V2_PREFIX + "/quote-regions")
public class V2QuoteRegionController {

    private final QuoteRegionService quoteRegionService;

    public V2QuoteRegionController(QuoteRegionService quoteRegionService) {
        this.quoteRegionService = quoteRegionService;
    }

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

    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.QUOTE_REGIONS_READ)
    public QuoteRegionResponse detail(@PathVariable Long id) {
        return quoteRegionService.detail(id);
    }

    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.QUOTE_REGIONS_CREATE)
    public ResponseEntity<QuoteRegionResponse> create(@Valid @RequestBody QuoteRegionRequest request) {
        return V2ResponseSupport.created("/quote-regions", quoteRegionService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.QUOTE_REGIONS_UPDATE)
    public QuoteRegionResponse update(@PathVariable Long id, @Valid @RequestBody QuoteRegionRequest request) {
        return quoteRegionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.QUOTE_REGIONS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        quoteRegionService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
