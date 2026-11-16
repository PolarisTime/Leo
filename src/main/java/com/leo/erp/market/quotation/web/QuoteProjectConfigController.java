package com.leo.erp.market.quotation.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.web.ResourceVersionPrecondition;
import com.leo.erp.market.quotation.QuotationProperties;
import com.leo.erp.market.quotation.service.QuoteProjectConfigService;
import com.leo.erp.market.quotation.service.QuoteProjectEffectiveBrandsService;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigRequest;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigResponse;
import com.leo.erp.market.quotation.web.dto.QuoteProjectEffectiveBrandsResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "比价项目配置")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/quote-project-configs")
public class QuoteProjectConfigController {

    private static final String VERSION_HEADER = ResourceVersionPrecondition.HEADER;
    private final QuoteProjectConfigService service;
    private final QuoteProjectEffectiveBrandsService effectiveBrandsService;
    private final QuotationProperties properties;

    public QuoteProjectConfigController(QuoteProjectConfigService service,
                                          QuoteProjectEffectiveBrandsService effectiveBrandsService,
                                          QuotationProperties properties) {
        this.service = service;
        this.effectiveBrandsService = effectiveBrandsService;
        this.properties = properties;
    }

    @Operation(summary = "查询项目比价配置",
            description = "响应形状不变, 值按项目资料派生: 品牌与顺序=项目允许品牌, 品种=项目品牌品种配置,"
                    + " 商品键/12米加价/兜底/指定品牌=项目资料 quote_* 列; 运费仍来自比价配置。"
                    + " 未配置运费时 version=0 且不落库。")
    @GetMapping("/{projectId}")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_READ)
    public QuoteProjectConfigResponse find(@PathVariable @SnowflakeId Long projectId) {
        return service.find(projectId);
    }

    @Operation(
            summary = "查询项目用于报单比价的有效品牌集合",
            description = "品牌事实唯一来源是项目资料(md_project.allowed_brands); V220 起不再回退到比价配置,"
                    + " 响应里的 fallback 恒为 false(字段保留仅为兼容)。占位品牌「基准价」仍被过滤。")
    @GetMapping("/{projectId}/effective-brands")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_READ)
    public QuoteProjectEffectiveBrandsResponse effectiveBrands(@PathVariable @SnowflakeId Long projectId) {
        return effectiveBrandsService.effectiveBrands(projectId);
    }

    @Operation(summary = "保存项目比价配置",
            description = "整体替换该项目配置(幂等); 不存在则创建。要求资源版本前置条件头 " + VERSION_HEADER
                    + "; 缺少版本返回 428, 版本不匹配返回 412; 响应头回传最新版本。")
    @PutMapping("/{projectId}")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public ResponseEntity<QuoteProjectConfigResponse> save(
            @PathVariable @SnowflakeId Long projectId,
            @Parameter(description = "资源版本(强比较), 如 3")
            @RequestHeader(value = VERSION_HEADER, required = false) String resourceVersion,
            @Valid @RequestBody QuoteProjectConfigRequest request) {
        QuoteProjectConfigResponse response = service.save(projectId, request,
                ResourceVersionPrecondition.parse(resourceVersion, properties.isRequireResourceVersion()));
        return ResponseEntity.ok()
                .header(VERSION_HEADER, String.valueOf(response.version()))
                .body(response);
    }
}
