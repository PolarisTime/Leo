package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.printtemplate.service.PrintOutputService;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import com.leo.erp.system.printtemplate.web.dto.PrintItemRowResponse;
import com.leo.erp.system.printtemplate.web.dto.PrintItemsQueryRequest;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordOutputResponse;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public PrintRecordOutputResponse fromRecord(@Valid @RequestBody @NotNull PrintRecordRequest payload) {
        return PrintRecordOutputResponse.from(printOutputService.generateFromRecord(
                payload.templateId(),
                payload.moduleKey(),
                payload.recordId(),
                payload.resolvedPrintOptions()
        ));
    }

    @PostMapping("/items")
    public List<PrintItemRowResponse> items(@Valid @RequestBody @NotNull PrintItemsQueryRequest request) {
        return printScriptService.listPrintItems(request.moduleKey(), request.recordIds())
                .stream()
                .map(PrintItemRowResponse::from)
                .toList();
    }
}
