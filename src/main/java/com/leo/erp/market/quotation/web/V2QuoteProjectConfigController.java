package com.leo.erp.market.quotation.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.market.quotation.QuotationProperties;
import com.leo.erp.market.quotation.service.QuoteProjectConfigService;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigRequest;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigResponse;
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
@RequestMapping(ApiVersion.V2_PREFIX + "/quote-project-configs")
public class V2QuoteProjectConfigController {

    private static final String VERSION_HEADER = ResourceVersionPrecondition.HEADER;
    private static final String VERSION_ALIAS_NOTE =
            "也接受兼容别名 If-Match(非标准: 数据库版本号是弱验证器, 不符合 RFC 9110 强比较要求)。";

    private final QuoteProjectConfigService service;
    private final QuotationProperties properties;

    public V2QuoteProjectConfigController(QuoteProjectConfigService service, QuotationProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Operation(summary = "查询项目比价配置", description = "按项目返回品牌/可选商品/指定品牌/兜底配置; 未配置时返回默认空配置")
    @GetMapping("/{projectId}")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_READ)
    public QuoteProjectConfigResponse find(@PathVariable Long projectId) {
        return service.find(projectId);
    }

    @Operation(summary = "保存项目比价配置",
            description = "整体替换该项目配置(幂等); 不存在则创建。要求资源版本前置条件头 " + VERSION_HEADER
                    + ", " + VERSION_ALIAS_NOTE + " 缺少版本返回 428, 版本不匹配返回 412; 响应头回传最新版本。")
    @PutMapping("/{projectId}")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public ResponseEntity<QuoteProjectConfigResponse> save(
            @PathVariable Long projectId,
            @Parameter(description = "资源版本(强比较), 如 3")
            @RequestHeader(value = VERSION_HEADER, required = false) String resourceVersion,
            @Parameter(description = "兼容别名, 非标准弱验证器用法")
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody QuoteProjectConfigRequest request) {
        QuoteProjectConfigResponse response = service.save(projectId, request,
                ResourceVersionPrecondition.parse(resourceVersion, ifMatch, properties.isRequireResourceVersion()));
        return ResponseEntity.ok()
                .header(VERSION_HEADER, String.valueOf(response.version()))
                .body(response);
    }
}
