package com.leo.erp.system.printtemplate.web;

import com.leo.erp.system.printtemplate.service.PrintOutput;
import com.leo.erp.system.printtemplate.service.PrintOutputService;
import com.leo.erp.system.printtemplate.service.PrintRecordItem;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordRequest;
import com.leo.erp.system.operationlog.support.OperationLoggable;
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
import com.leo.erp.common.api.ApiVersion;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/print")
public class V2PrintScriptController {

    private final PrintScriptService printScriptService;
    private final PrintOutputService printOutputService;

    public V2PrintScriptController(PrintScriptService printScriptService,
                                   PrintOutputService printOutputService) {
        this.printScriptService = printScriptService;
        this.printOutputService = printOutputService;
    }

    @PostMapping("/record")
    @OperationLoggable(
            moduleName = "打印",
            moduleNameField = "moduleKey",
            actionType = "打印",
            businessNoFields = {"businessNo"},
            recordIdField = "recordId",
            moduleKeyField = "moduleKey"
    )
    public PrintOutput fromRecord(@Valid @RequestBody @NotNull PrintRecordRequest payload) {
        return printOutputService.generateFromRecord(
                payload.templateId(),
                payload.moduleKey(),
                payload.recordId(),
                payload.resolvedPrintOptions()
        );
    }

    @PostMapping("/items")
    public List<PrintRecordItem> items(@RequestBody @NotNull Map<String, Object> payload) {
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        return printScriptService.listPrintItems(moduleKey, recordIds(payload.get("recordIds")));
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
