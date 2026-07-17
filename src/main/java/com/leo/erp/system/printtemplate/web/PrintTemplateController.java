package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import org.springframework.security.access.prepost.PreAuthorize;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.printtemplate.service.PrintTemplateService;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@Validated
@RequestMapping("/print-templates")
public class PrintTemplateController {

    private final PrintTemplateService printTemplateService;
    private final ModulePermissionGuard modulePermissionGuard;

    public PrintTemplateController(PrintTemplateService printTemplateService,
                                   ModulePermissionGuard modulePermissionGuard) {
        this.printTemplateService = printTemplateService;
        this.modulePermissionGuard = modulePermissionGuard;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<PrintTemplateResponse>> list(@AuthenticationPrincipal SecurityPrincipal principal,
                                                         @RequestParam @NotBlank @Size(max = 64) String billType) {
        modulePermissionGuard.requireResourcePermissionAny(
                principal,
                billType,
                "print",
                "read"
        );
        return ApiResponse.success(printTemplateService.listByBillType(billType));
    }

    @PostMapping
    @PreAuthorize("@rbac.check('print-template', 'create')")
    @OperationLoggable(moduleName = "打印模板", actionType = "新增", businessNoFields = {"billType", "templateName"})
    public ApiResponse<PrintTemplateResponse> create(@AuthenticationPrincipal SecurityPrincipal principal,
                                                     @Valid @RequestBody PrintTemplateRequest request) {
        modulePermissionGuard.requireResourcePermission(principal, request.billType(), "update");
        return ApiResponse.success("创建成功", printTemplateService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbac.check('print-template', 'update')")
    @OperationLoggable(moduleName = "打印模板", actionType = "编辑", businessNoFields = {"billType", "templateName"})
    public ApiResponse<PrintTemplateResponse> update(@AuthenticationPrincipal SecurityPrincipal principal,
                                                     @PathVariable @Positive Long id,
                                                     @Valid @RequestBody PrintTemplateRequest request) {
        String currentBillType = printTemplateService.getBillType(id);
        modulePermissionGuard.requireResourcePermission(principal, currentBillType, "update");
        if (!currentBillType.equals(request.billType())) {
            modulePermissionGuard.requireResourcePermission(principal, request.billType(), "update");
        }
        return ApiResponse.success("更新成功", printTemplateService.update(id, request));
    }

    @PostMapping(value = "/{id}/upload-json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rbac.check('print-template', 'update')")
    @OperationLoggable(moduleName = "打印模板", actionType = "上传 JSON", businessNoFields = {"id"})
    public ApiResponse<PrintTemplateResponse> uploadJson(@AuthenticationPrincipal SecurityPrincipal principal,
                                                         @PathVariable @Positive Long id,
                                                         @RequestParam("file") MultipartFile file) {
        modulePermissionGuard.requireResourcePermissionAny(
                principal,
                printTemplateService.getBillType(id),
                "update"
        );
        return ApiResponse.success("上传成功", printTemplateService.uploadJson(id, file));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.check('print-template', 'delete')")
    @OperationLoggable(moduleName = "打印模板", actionType = "删除")
    public ApiResponse<Void> delete(@AuthenticationPrincipal SecurityPrincipal principal,
                                    @PathVariable @Positive Long id) {
        modulePermissionGuard.requireResourcePermissionAny(
                principal,
                printTemplateService.getBillType(id),
                "update"
        );
        printTemplateService.delete(id);
        return ApiResponse.success("删除成功");
    }
}
