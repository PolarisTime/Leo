package com.leo.erp.market.quotation.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.market.quotation.service.QuoteProjectConfigService;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigRequest;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping(ApiVersion.V2_PREFIX + "/quote-project-configs")
public class V2QuoteProjectConfigController {

    private final QuoteProjectConfigService service;

    public V2QuoteProjectConfigController(QuoteProjectConfigService service) {
        this.service = service;
    }

    @Operation(summary = "查询项目比价配置", description = "按项目返回品牌/可选商品/指定品牌/兜底配置; 未配置时返回默认空配置")
    @GetMapping("/{projectId}")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_READ)
    public QuoteProjectConfigResponse find(@PathVariable Long projectId) {
        return service.find(projectId);
    }

    @Operation(summary = "保存项目比价配置", description = "整体替换该项目配置(幂等); 不存在则创建")
    @PutMapping("/{projectId}")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public QuoteProjectConfigResponse save(@PathVariable Long projectId,
                                           @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                           @Valid @RequestBody QuoteProjectConfigRequest request) {
        return service.save(projectId, request, IfMatchVersion.parse(ifMatch));
    }
}
