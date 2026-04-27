package com.leo.erp.attachment.web;

import com.leo.erp.attachment.mapper.AttachmentWebMapper;
import com.leo.erp.attachment.service.AttachmentService;
import com.leo.erp.attachment.service.AttachmentService.AttachmentDownloadPayload;
import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.UploadRuleService;
import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final AttachmentWebMapper attachmentWebMapper;
    private final ModulePermissionGuard modulePermissionGuard;
    private final UploadRuleService uploadRuleService;
    private final AttachmentRecordAccessService attachmentRecordAccessService;

    public AttachmentController(AttachmentService attachmentService,
                                AttachmentWebMapper attachmentWebMapper,
                                ModulePermissionGuard modulePermissionGuard,
                                UploadRuleService uploadRuleService,
                                AttachmentRecordAccessService attachmentRecordAccessService) {
        this.attachmentService = attachmentService;
        this.attachmentWebMapper = attachmentWebMapper;
        this.modulePermissionGuard = modulePermissionGuard;
        this.uploadRuleService = uploadRuleService;
        this.attachmentRecordAccessService = attachmentRecordAccessService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    @OperationLoggable(moduleName = "附件管理", actionType = "上传附件")
    public ApiResponse<AttachmentUploadResponse> upload(@AuthenticationPrincipal SecurityPrincipal principal,
                                                        @RequestParam String moduleKey,
                                                        @RequestParam("file") MultipartFile file,
                                                        @RequestParam(required = false) String sourceType) throws IOException {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, moduleKey, "update");
        if (!uploadRuleService.isPageUploadEnabled(normalizedModuleKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前页面未启用附件标志");
        }
        AttachmentView result = attachmentService.upload(file, sourceType, normalizedModuleKey);
        return ApiResponse.success("上传成功", attachmentWebMapper.toUploadResponse(result));
    }

    @GetMapping("/{id}/download")
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ResponseEntity<?> download(@AuthenticationPrincipal SecurityPrincipal principal,
                                      @PathVariable Long id,
                                      @RequestParam String moduleKey,
                                      @RequestParam String accessKey) {
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        attachmentRecordAccessService.assertAttachmentAccessible(principal, moduleKey, "read", id);
        AttachmentDownloadPayload payload = attachmentService.loadForDownload(id, accessKey);
        return buildFileResponse(payload, false);
    }

    @GetMapping("/{id}/preview")
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ResponseEntity<?> preview(@AuthenticationPrincipal SecurityPrincipal principal,
                                     @PathVariable Long id,
                                     @RequestParam String moduleKey,
                                     @RequestParam String accessKey) {
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        attachmentRecordAccessService.assertAttachmentAccessible(principal, moduleKey, "read", id);
        AttachmentDownloadPayload payload = attachmentService.loadForPreview(id, accessKey);
        return buildFileResponse(payload, true);
    }

    private ResponseEntity<?> buildFileResponse(AttachmentDownloadPayload payload, boolean inline) {
        MediaType mediaType = (payload.contentType() == null || payload.contentType().isBlank())
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(payload.contentType());
        ContentDisposition contentDisposition = inline
                ? ContentDisposition.inline().filename(payload.fileName(), StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(payload.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(payload.resource());
    }
}
