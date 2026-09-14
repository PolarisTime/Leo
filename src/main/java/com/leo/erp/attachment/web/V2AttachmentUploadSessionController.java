package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.AttachmentWebService;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadPrepareRequest;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadPrepareResponse;
import com.leo.erp.attachment.web.dto.AttachmentUploadCompletionRequest;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * 附件直传会话资源接口。
 * 直传准备被建模为 attachment-upload-sessions 资源：创建会话即签发预签名上传地址与凭证；
 * 直传完成被建模为会话下的 completions 子资源，创建完成记录即校验并落库为附件。
 */
@Tag(name = "附件直传会话")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/attachment-upload-sessions")
public class V2AttachmentUploadSessionController {

    private final AttachmentWebService attachmentWebService;
    private final AttachmentRecordAccessService attachmentRecordAccessService;

    public V2AttachmentUploadSessionController(AttachmentWebService attachmentWebService,
                                               AttachmentRecordAccessService attachmentRecordAccessService) {
        this.attachmentWebService = attachmentWebService;
        this.attachmentRecordAccessService = attachmentRecordAccessService;
    }

    @Operation(summary = "创建附件直传会话",
            description = "创建直传会话资源并返回预签名上传地址、直传凭证与有效期。会话标识为返回体中的 attachmentId；重复提交建议携带 Idempotency-Key。")
    @IdempotencyRequired
    @PostMapping
    @V2Created
    @OperationLoggable(moduleName = "附件管理", actionType = "创建附件直传会话")
    @RequirePermission(PermissionCodes.ATTACHMENT_UPLOAD_SESSIONS_CREATE)
    public ResponseEntity<AttachmentDirectUploadPrepareResponse> create(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam @NotBlank(message = "模块标识不能为空") String moduleKey,
            @Valid @RequestBody AttachmentDirectUploadPrepareRequest request) {
        String normalizedModuleKey = attachmentRecordAccessService.normalizeModuleKey(moduleKey);
        AttachmentDirectUploadPrepareResponse response =
                attachmentWebService.prepareDirectUpload(request, normalizedModuleKey, principal.id());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ApiVersion.V2_PREFIX)
                .path("/attachment-upload-sessions")
                .pathSegment(String.valueOf(response.attachmentId()))
                .build()
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "完成附件直传会话",
            description = "在直传会话下创建完成记录，校验对象存储中已上传内容并落库为附件资源。请求体仅需直传凭证，会话标识取自路径。")
    @IdempotencyRequired
    @PostMapping("/{sessionId}/completions")
    @V2Created
    @OperationLoggable(moduleName = "附件管理", actionType = "完成附件直传")
    @RequirePermission(PermissionCodes.ATTACHMENT_UPLOAD_SESSIONS_COMPLETE)
    public ResponseEntity<AttachmentUploadResponse> complete(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable Long sessionId,
            @RequestParam @NotBlank(message = "模块标识不能为空") String moduleKey,
            @Valid @RequestBody AttachmentUploadCompletionRequest request) {
        String normalizedModuleKey = attachmentRecordAccessService.normalizeModuleKey(moduleKey);
        return V2ResponseSupport.created(
                "/attachments",
                attachmentWebService.completeDirectUpload(
                        sessionId, request.token(), normalizedModuleKey, principal.id())
        );
    }
}
