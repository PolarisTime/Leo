package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.printtemplate.service.PrintOutput;
import com.leo.erp.system.printtemplate.service.PrintOutputService;
import com.leo.erp.system.printtemplate.service.PrintRecordItem;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@Validated
@RequestMapping("/print")
public class PrintScriptController {

    private final PrintScriptService printScriptService;
    private final PrintOutputService printOutputService;

    public PrintScriptController(PrintScriptService printScriptService,
                                 PrintOutputService printOutputService) {
        this.printScriptService = printScriptService;
        this.printOutputService = printOutputService;
    }

    /** 统一打印接口：由输出服务封装 PDF 与坐标套打响应。 */
    @PostMapping("/record")
    @OperationLoggable(
            moduleName = "打印",
            moduleNameField = "moduleKey",
            actionType = "打印",
            businessNoFields = {"businessNo"},
            recordIdField = "recordId",
            moduleKeyField = "moduleKey"
    )
    public ApiResponse<PrintOutput> fromRecord(
            @Valid @RequestBody @NotNull PrintRecordRequest payload) {
        String moduleKey = payload.moduleKey();
        PrintOutput result = printOutputService.generateFromRecord(
                payload.templateId(),
                moduleKey,
                payload.recordId(),
                payload.resolvedPrintOptions()
        );
        return ApiResponse.success(result);
    }

    @PostMapping("/brands")
    public ApiResponse<List<String>> brands(
            @RequestBody @NotNull Map<String, Object> payload) {
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        return ApiResponse.success(printScriptService.listBrands(moduleKey, recordIds(payload.get("recordIds"))));
    }

    @PostMapping("/items")
    public ApiResponse<List<PrintRecordItem>> items(
            @RequestBody @NotNull Map<String, Object> payload) {
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        return ApiResponse.success(printScriptService.listPrintItems(moduleKey, recordIds(payload.get("recordIds"))));
    }

    private List<Long> recordIds(Object rawRecordIds) {
        if (!(rawRecordIds instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::recordId)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Long> recordId(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(text));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
