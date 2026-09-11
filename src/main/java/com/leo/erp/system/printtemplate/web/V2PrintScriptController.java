package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.printtemplate.service.PrintOutputService;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import com.leo.erp.system.printtemplate.web.dto.PrintItemRowResponse;
import com.leo.erp.system.printtemplate.web.dto.PrintItemsQueryRequest;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordOutputResponse;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @deprecated 旧的打印输出动作端点，已由资源型端点替代，保留用于兼容旧调用方：
 * <ul>
 *     <li>生成打印导出：{@link V2PrintExportController} 的 {@code POST /print-exports}</li>
 *     <li>查询打印明细：{@link V2PrintPreviewController} 的 {@code GET /print-previews/items}</li>
 * </ul>
 * 待调用方迁移完成后移除。
 */
@Deprecated
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/print-outputs")
public class V2PrintScriptController {

    private final PrintScriptService printScriptService;
    private final PrintOutputService printOutputService;

    public V2PrintScriptController(PrintScriptService printScriptService,
                                   PrintOutputService printOutputService) {
        this.printScriptService = printScriptService;
        this.printOutputService = printOutputService;
    }

    @Operation(summary = "（已废弃）生成打印输出", deprecated = true,
            description = "请改用 POST /print-exports 创建打印导出资源")
    @PostMapping
    @OperationLoggable(
            moduleName = "打印",
            moduleNameField = "moduleKey",
            actionType = "打印",
            businessNoFields = {"businessNo"},
            recordIdField = "recordId",
            moduleKeyField = "moduleKey"
    )
    public PrintRecordOutputResponse fromRecord(@Valid @RequestBody @NotNull PrintRecordRequest payload) {
        return PrintRecordOutputResponse.from(printOutputService.generateFromRecord(
                payload.templateId(),
                payload.moduleKey(),
                payload.recordId(),
                payload.resolvedPrintOptions()
        ));
    }

    @Operation(summary = "（已废弃）查询打印明细", deprecated = true,
            description = "请改用 GET /print-previews/items 分页查询打印明细")
    @PostMapping("/items")
    public List<PrintItemRowResponse> items(@Valid @RequestBody @NotNull PrintItemsQueryRequest request) {
        return printScriptService.listPrintItems(request.moduleKey(), request.recordIds())
                .stream()
                .map(PrintItemRowResponse::from)
                .toList();
    }
}
