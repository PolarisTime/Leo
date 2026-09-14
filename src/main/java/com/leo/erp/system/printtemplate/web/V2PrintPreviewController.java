package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.system.printtemplate.web.dto.PrintItemRowResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 打印预览资源接口。
 * 打印明细查询为只读操作，建模为带分页与明确上界的 GET 资源，
 * 替代原 {@code POST /print-outputs/items}。
 */
@Tag(name = "打印预览")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/print-previews")
public class V2PrintPreviewController {

    private final PrintScriptService printScriptService;

    public V2PrintPreviewController(PrintScriptService printScriptService) {
        this.printScriptService = printScriptService;
    }

    @Operation(summary = "分页查询打印明细（打印预览数据）")
    @GetMapping("/items")
    @RequirePermission(PermissionCodes.PRINT_PREVIEWS_READ)
    public PageResponse<PrintItemRowResponse> items(
            @BindPageQuery PageQuery query,
            @RequestParam @NotBlank @Size(max = 64) String moduleKey,
            @RequestParam @Size(min = 1, max = 200) List<@Positive Long> recordIds) {
        return PageResponse.from(
                printScriptService.pagePrintItems(moduleKey, recordIds, query)
                        .map(PrintItemRowResponse::from));
    }
}
