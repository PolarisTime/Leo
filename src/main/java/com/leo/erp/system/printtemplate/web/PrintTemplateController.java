package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.printtemplate.service.PrintTemplateService;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

import java.util.List;

@RestController
@Validated
@RequestMapping("/print-templates")
public class PrintTemplateController {

    private final PrintTemplateService printTemplateService;

    public PrintTemplateController(PrintTemplateService printTemplateService) {
        this.printTemplateService = printTemplateService;
    }

    @GetMapping
    @RequiresPermission(resource = "print-template", action = "read")
    public ApiResponse<List<PrintTemplateResponse>> list(@RequestParam @NotBlank @Size(max = 64) String billType) {
        return ApiResponse.success(printTemplateService.listByBillType(billType));
    }

    @GetMapping("/default")
    @RequiresPermission(resource = "print-template", action = "read")
    public ApiResponse<PrintTemplateResponse> getDefault(@RequestParam @NotBlank @Size(max = 64) String billType) {
        return ApiResponse.success(printTemplateService.getDefaultByBillType(billType));
    }

    @PostMapping
    @RequiresPermission(resource = "print-template", action = "create")
    @OperationLoggable(moduleName = "打印模板", actionType = "新增", businessNoFields = {"billType", "templateName"})
    public ApiResponse<PrintTemplateResponse> create(@Valid @RequestBody PrintTemplateRequest request) {
        return ApiResponse.success("创建成功", printTemplateService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "print-template", action = "update")
    @OperationLoggable(moduleName = "打印模板", actionType = "编辑", businessNoFields = {"billType", "templateName"})
    public ApiResponse<PrintTemplateResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody PrintTemplateRequest request) {
        return ApiResponse.success("更新成功", printTemplateService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "print-template", action = "delete")
    @OperationLoggable(moduleName = "打印模板", actionType = "删除")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        printTemplateService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
