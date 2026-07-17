package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentDownloadResource;
import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.AttachmentService;
import com.leo.erp.attachment.service.AttachmentWebService;
import com.leo.erp.attachment.web.dto.AttachmentAccessUrlResponse;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadCompleteRequest;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadPrepareRequest;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadPrepareResponse;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.RateLimit;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@Validated
@RequestMapping("/attachments")
public class AttachmentController {

    private static final double ATTACHMENT_ACCESS_RATE = 2.0;
    private static final int ATTACHMENT_ACCESS_CAPACITY = 20;
    private final AttachmentService attachmentService;
    private final AttachmentWebService attachmentWebService;
    private final ModulePermissionGuard modulePermissionGuard;
    private final AttachmentRecordAccessService attachmentRecordAccessService;

    public AttachmentController(AttachmentService attachmentService,
                                AttachmentWebService attachmentWebService,
                                ModulePermissionGuard modulePermissionGuard,
                                AttachmentRecordAccessService attachmentRecordAccessService) {
        this.attachmentService = attachmentService;
        this.attachmentWebService = attachmentWebService;
        this.modulePermissionGuard = modulePermissionGuard;
        this.attachmentRecordAccessService = attachmentRecordAccessService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(rate = 0.5, capacity = 10)
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    @OperationLoggable(moduleName = "附件管理", actionType = "上传附件")
    public ApiResponse<AttachmentUploadResponse> upload(@AuthenticationPrincipal SecurityPrincipal principal,
                                                        @RequestParam @NotBlank(message = "模块标识不能为空") String moduleKey,
                                                        @RequestParam("file") MultipartFile file,
                                                        @RequestParam(required = false) String sourceType) throws IOException {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, moduleKey, "update");
        return ApiResponse.success("上传成功", attachmentWebService.upload(file, sourceType, normalizedModuleKey));
    }

    @PostMapping("/direct-upload/prepare")
    @RateLimit(rate = 0.5, capacity = 10)
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    @OperationLoggable(moduleName = "附件管理", actionType = "生成附件直传地址")
    public ApiResponse<AttachmentDirectUploadPrepareResponse> prepareDirectUpload(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam @NotBlank(message = "模块标识不能为空") String moduleKey,
            @Valid @RequestBody AttachmentDirectUploadPrepareRequest request) {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, moduleKey, "update");
        return ApiResponse.success("直传地址生成成功",
                attachmentWebService.prepareDirectUpload(request, normalizedModuleKey, principal.id()));
    }

    @PostMapping("/direct-upload/complete")
    @RateLimit(rate = 0.5, capacity = 10)
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    @OperationLoggable(moduleName = "附件管理", actionType = "完成附件直传")
    public ApiResponse<AttachmentUploadResponse> completeDirectUpload(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam @NotBlank(message = "模块标识不能为空") String moduleKey,
            @Valid @RequestBody AttachmentDirectUploadCompleteRequest request) {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, moduleKey, "update");
        return ApiResponse.success("上传成功",
                attachmentWebService.completeDirectUpload(request, normalizedModuleKey, principal.id()));
    }

    @GetMapping("/{id}/access-url")
    @RateLimit(rate = ATTACHMENT_ACCESS_RATE, capacity = ATTACHMENT_ACCESS_CAPACITY)
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ApiResponse<AttachmentAccessUrlResponse> accessUrl(@AuthenticationPrincipal SecurityPrincipal principal,
                                                              @PathVariable Long id,
                                                              @RequestParam String moduleKey,
                                                              @RequestParam String accessKey,
                                                              @RequestParam(defaultValue = "false") boolean inline) {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        attachmentRecordAccessService.assertAttachmentAccessible(principal, normalizedModuleKey, "read", id);
        AttachmentService.PresignedAttachmentUrl presignedUrl =
                attachmentService.createPresignedAccessUrl(id, accessKey, inline);
        return ApiResponse.success(new AttachmentAccessUrlResponse(
                presignedUrl == null ? null : presignedUrl.url().toString(),
                inline,
                presignedUrl != null
        ));
    }

    @GetMapping("/{id}/download")
    @RateLimit(rate = ATTACHMENT_ACCESS_RATE, capacity = ATTACHMENT_ACCESS_CAPACITY)
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ResponseEntity<Resource> download(@AuthenticationPrincipal SecurityPrincipal principal,
                                             @PathVariable Long id,
                                             @RequestParam String moduleKey,
                                             @RequestParam String accessKey) {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        attachmentRecordAccessService.assertAttachmentAccessible(principal, normalizedModuleKey, "read", id);
        AttachmentService.PresignedAttachmentUrl presignedUrl =
                attachmentService.createPresignedAccessUrl(id, accessKey, false);
        if (presignedUrl != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(presignedUrl.url()).build();
        }
        return buildFileResponse(attachmentService.loadDownloadResource(id, accessKey, false));
    }

    @GetMapping("/{id}/preview")
    @RateLimit(rate = ATTACHMENT_ACCESS_RATE, capacity = ATTACHMENT_ACCESS_CAPACITY)
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ResponseEntity<Resource> preview(@AuthenticationPrincipal SecurityPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestParam String moduleKey,
                                            @RequestParam String accessKey) {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        attachmentRecordAccessService.assertAttachmentAccessible(principal, normalizedModuleKey, "read", id);
        AttachmentService.PresignedAttachmentUrl presignedUrl =
                attachmentService.createPresignedAccessUrl(id, accessKey, true);
        if (presignedUrl != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(presignedUrl.url()).build();
        }
        return buildFileResponse(attachmentService.loadDownloadResource(id, accessKey, true));
    }

    private ResponseEntity<Resource> buildFileResponse(AttachmentDownloadResource payload) {
        return ResponseEntity.ok()
                .contentType(payload.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, payload.contentDisposition())
                .body(payload.resource());
    }
}
