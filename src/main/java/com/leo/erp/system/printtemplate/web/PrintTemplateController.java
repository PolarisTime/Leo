package com.leo.erp.system.printtemplate.web;

import com.leo.erp.system.printtemplate.service.PrintTemplateService;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.MediaType;
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
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.api.ResponseSupport;
import com.leo.erp.common.api.Created;
import com.leo.erp.common.api.NoContent;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/print-templates")
public class PrintTemplateController {

    private static final long MAX_UPLOAD_JSON_BYTES = 1024L * 1024L;

    private final PrintTemplateService printTemplateService;

    public PrintTemplateController(PrintTemplateService printTemplateService) {
        this.printTemplateService = printTemplateService;
    }

    @Operation(summary = "查询打印模板列表", description = "按单据类型 billType 返回该类型的全部模板（不分页，按模板顺序）。需 PRINT_TEMPLATES_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.PRINT_TEMPLATES_READ)
    public List<PrintTemplateResponse> list(@RequestParam @NotBlank @Size(max = 64) String billType) {
        return printTemplateService.listByBillType(billType);
    }

    @Operation(summary = "创建打印模板", description = "成功返回 201 与 Location /print-templates/{id}，响应体为新建模板（id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 PRINT_TEMPLATES_CREATE 权限。")
    @PostMapping
    @OperationLoggable(moduleName = "打印模板", actionType = "新增", businessNoFields = {"billType", "templateName"})
    @Created
    @RequirePermission(PermissionCodes.PRINT_TEMPLATES_CREATE)
    public ResponseEntity<PrintTemplateResponse> create(@Valid @RequestBody PrintTemplateRequest request) {
        return ResponseSupport.created("/print-templates", printTemplateService.create(request));
    }

    @Operation(summary = "更新打印模板", description = "PUT 整体替换模板元数据，成功返回 200 与更新后的模板；路径 id 为雪花 ID 字符串。重复提交需 Idempotency-Key（类级强制）。需 PRINT_TEMPLATES_UPDATE 权限。")
    @PutMapping("/{id}")
    @OperationLoggable(moduleName = "打印模板", actionType = "编辑", businessNoFields = {"billType", "templateName"})
    @RequirePermission(PermissionCodes.PRINT_TEMPLATES_UPDATE)
    public PrintTemplateResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody PrintTemplateRequest request) {
        return printTemplateService.update(id, request);
    }

    @Operation(summary = "替换打印模板内容", description = "以 multipart 上传模板 JSON 并替换模板内容：单文件超过 1MB 入口早拒（400），成功返回 200 与更新后的模板。重复提交需 Idempotency-Key。需 PRINT_TEMPLATES_UPDATE 权限。")
    @PutMapping(value = "/{id}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @OperationLoggable(moduleName = "打印模板", actionType = "替换内容", businessNoFields = {"id"})
    @RequirePermission(PermissionCodes.PRINT_TEMPLATES_UPDATE)
    public PrintTemplateResponse replaceContent(@PathVariable @SnowflakeId Long id, @RequestParam("file") MultipartFile file) {
        // 全局 multipart 限额为 20MB，本端点业务上限 1MB：入口早拒，避免超限文件完整传输后才被拒。
        if (file != null && file.getSize() > MAX_UPLOAD_JSON_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能超过 1MB");
        }
        return printTemplateService.uploadJson(id, file);
    }

    @Operation(summary = "删除打印模板", description = "幂等删除，成功返回 204 No Content；重复删除结果一致。需 PRINT_TEMPLATES_DELETE 权限。")
    @DeleteMapping("/{id}")
    @OperationLoggable(moduleName = "打印模板", actionType = "删除")
    @NoContent
    @RequirePermission(PermissionCodes.PRINT_TEMPLATES_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        printTemplateService.delete(id);
        return ResponseSupport.noContent();
    }
}
