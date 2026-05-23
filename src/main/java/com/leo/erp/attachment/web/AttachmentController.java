package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentDownloadResource;
import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.AttachmentService;
import com.leo.erp.attachment.service.AttachmentWebService;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@Validated
@RequestMapping("/attachments")
public class AttachmentController {

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

    private boolean isAdmin(SecurityPrincipal principal) {
        if (principal == null) return false;
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.contains("系统管理员") || a.contains("超级管理员") || a.contains("全部数据"));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    @OperationLoggable(moduleName = "附件管理", actionType = "上传附件")
    public ApiResponse<AttachmentUploadResponse> upload(@AuthenticationPrincipal SecurityPrincipal principal,
                                                        @RequestParam @NotBlank(message = "模块标识不能为空") String moduleKey,
                                                        @RequestParam("file") MultipartFile file,
                                                        @RequestParam(required = false) String sourceType) throws IOException {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, moduleKey, "update");
        return ApiResponse.success("上传成功", attachmentWebService.upload(file, sourceType, normalizedModuleKey));
    }

    @GetMapping("/{id}/download")
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ResponseEntity<Resource> download(@AuthenticationPrincipal SecurityPrincipal principal,
                                             @PathVariable Long id,
                                             @RequestParam String moduleKey,
                                             @RequestParam String accessKey) {
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        attachmentRecordAccessService.assertAttachmentAccessible(principal, moduleKey, "read", id);
        boolean admin = isAdmin(principal);
        return buildFileResponse(attachmentService.loadDownloadResource(
                id, accessKey, false, !admin, principal.getUsername()));
    }

    @GetMapping("/{id}/preview")
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ResponseEntity<Resource> preview(@AuthenticationPrincipal SecurityPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestParam String moduleKey,
                                            @RequestParam String accessKey) {
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        attachmentRecordAccessService.assertAttachmentAccessible(principal, moduleKey, "read", id);
        boolean admin = isAdmin(principal);
        return buildFileResponse(attachmentService.loadDownloadResource(
                id, accessKey, true, !admin, principal.getUsername()));
    }

    private ResponseEntity<Resource> buildFileResponse(AttachmentDownloadResource payload) {
        return ResponseEntity.ok()
                .contentType(payload.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, payload.contentDisposition())
                .body(payload.resource());
    }
}
