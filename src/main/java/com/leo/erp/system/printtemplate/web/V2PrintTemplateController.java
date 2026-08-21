package com.leo.erp.system.printtemplate.web;

import com.leo.erp.system.printtemplate.service.PrintTemplateService;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/print-templates")
public class V2PrintTemplateController {

    private static final long MAX_UPLOAD_JSON_BYTES = 1024L * 1024L;

    private final PrintTemplateService printTemplateService;

    public V2PrintTemplateController(PrintTemplateService printTemplateService) {
        this.printTemplateService = printTemplateService;
    }

    @GetMapping
    public List<PrintTemplateResponse> list(@RequestParam @NotBlank @Size(max = 64) String billType) {
        return printTemplateService.listByBillType(billType);
    }

    @PostMapping
    @OperationLoggable(moduleName = "打印模板", actionType = "新增", businessNoFields = {"billType", "templateName"})
    @V2Created
    public ResponseEntity<PrintTemplateResponse> create(@Valid @RequestBody PrintTemplateRequest request) {
        return V2ResponseSupport.created("/print-templates", printTemplateService.create(request));
    }

    @PutMapping("/{id}")
    @OperationLoggable(moduleName = "打印模板", actionType = "编辑", businessNoFields = {"billType", "templateName"})
    public PrintTemplateResponse update(@PathVariable @Positive Long id, @Valid @RequestBody PrintTemplateRequest request) {
        return printTemplateService.update(id, request);
    }

    @PutMapping(value = "/{id}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @OperationLoggable(moduleName = "打印模板", actionType = "替换内容", businessNoFields = {"id"})
    public PrintTemplateResponse replaceContent(@PathVariable @Positive Long id, @RequestParam("file") MultipartFile file) {
        // 全局 multipart 限额为 20MB，本端点业务上限 1MB：入口早拒，避免超限文件完整传输后才被拒。
        if (file != null && file.getSize() > MAX_UPLOAD_JSON_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能超过 1MB");
        }
        return printTemplateService.uploadJson(id, file);
    }

    @DeleteMapping("/{id}")
    @OperationLoggable(moduleName = "打印模板", actionType = "删除")
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        printTemplateService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
