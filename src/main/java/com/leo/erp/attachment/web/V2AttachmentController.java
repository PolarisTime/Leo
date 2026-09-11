package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentDownloadResource;
import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.AttachmentService;
import com.leo.erp.attachment.service.AttachmentWebService;
import com.leo.erp.attachment.web.dto.AttachmentAccessUrlResponse;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/attachments")
public class V2AttachmentController {

    private final AttachmentService attachmentService;
    private final AttachmentWebService attachmentWebService;
    private final AttachmentRecordAccessService attachmentRecordAccessService;

    public V2AttachmentController(AttachmentService attachmentService,
                                  AttachmentWebService attachmentWebService,
                                  AttachmentRecordAccessService attachmentRecordAccessService) {
        this.attachmentService = attachmentService;
        this.attachmentWebService = attachmentWebService;
        this.attachmentRecordAccessService = attachmentRecordAccessService;
    }

    @IdempotencyRequired
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @OperationLoggable(moduleName = "附件管理", actionType = "上传附件")
    @V2Created
    public ResponseEntity<AttachmentUploadResponse> upload(@AuthenticationPrincipal SecurityPrincipal principal, @RequestParam @NotBlank(message = "模块标识不能为空") String moduleKey, @RequestParam("file") MultipartFile file, @RequestParam(required = false) String sourceType) throws IOException {
        String normalizedModuleKey = attachmentRecordAccessService.normalizeModuleKey(moduleKey);
        return V2ResponseSupport.created(
                "/attachments",
                attachmentWebService.upload(file, sourceType, normalizedModuleKey, principal.id())
        );
    }

    @GetMapping("/{id}/access-url")
    public AttachmentAccessUrlResponse accessUrl(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable Long id, @RequestParam String moduleKey, @RequestParam String accessKey, @RequestParam(defaultValue = "false") boolean inline) {
        String normalizedModuleKey = attachmentRecordAccessService.normalizeModuleKey(moduleKey);
        attachmentRecordAccessService.assertAttachmentAccessible(principal, normalizedModuleKey, id);
        AttachmentService.PresignedAttachmentUrl presignedUrl =
                attachmentService.createPresignedAccessUrl(id, accessKey, inline);
        return new AttachmentAccessUrlResponse(
                presignedUrl == null ? null : presignedUrl.url().toString(),
                inline,
                presignedUrl != null
        );
    }

    /**
     * 资源型附件内容读取端点：文件内容即资源表示，通过 disposition 区分下载与内联预览。
     */
    @GetMapping("/{id}/content")
    @Operation(summary = "获取附件内容",
            description = "读取附件二进制内容。disposition=attachment 触发浏览器下载（默认），disposition=inline 用于内联预览；非法的 disposition 返回 400。")
    public ResponseEntity<Resource> content(@AuthenticationPrincipal SecurityPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestParam String moduleKey,
                                            @RequestParam String accessKey,
                                            @RequestParam(defaultValue = "attachment") String disposition) {
        return loadContent(principal, id, moduleKey, accessKey, resolveInline(disposition));
    }

    private boolean resolveInline(String disposition) {
        if (disposition == null || disposition.isBlank() || "attachment".equalsIgnoreCase(disposition)) {
            return false;
        }
        if ("inline".equalsIgnoreCase(disposition)) {
            return true;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "disposition 仅支持 inline 或 attachment");
    }

    private ResponseEntity<Resource> loadContent(SecurityPrincipal principal,
                                                 Long id,
                                                 String moduleKey,
                                                 String accessKey,
                                                 boolean inline) {
        String normalizedModuleKey = attachmentRecordAccessService.normalizeModuleKey(moduleKey);
        attachmentRecordAccessService.assertAttachmentAccessible(principal, normalizedModuleKey, id);
        AttachmentService.PresignedAttachmentUrl presignedUrl =
                attachmentService.createPresignedAccessUrl(id, accessKey, inline);
        if (presignedUrl != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(presignedUrl.url()).build();
        }
        return buildFileResponse(attachmentService.loadDownloadResource(id, accessKey, inline));
    }

    private ResponseEntity<Resource> buildFileResponse(AttachmentDownloadResource payload) {
        return ResponseEntity.ok()
                .contentType(payload.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, payload.contentDisposition())
                .body(payload.resource());
    }
}
