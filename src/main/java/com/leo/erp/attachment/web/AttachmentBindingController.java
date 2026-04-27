package com.leo.erp.attachment.web;

import com.leo.erp.attachment.mapper.AttachmentWebMapper;
import com.leo.erp.attachment.service.AttachmentBindingService;
import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.UploadRuleService;
import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.attachment.web.dto.AttachmentBindingRequest;
import com.leo.erp.attachment.web.dto.AttachmentBindingResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/attachments/bindings")
public class AttachmentBindingController {

    private final AttachmentBindingService attachmentBindingService;
    private final AttachmentWebMapper attachmentWebMapper;
    private final ModulePermissionGuard modulePermissionGuard;
    private final UploadRuleService uploadRuleService;
    private final AttachmentRecordAccessService attachmentRecordAccessService;

    public AttachmentBindingController(AttachmentBindingService attachmentBindingService,
                                       AttachmentWebMapper attachmentWebMapper,
                                       ModulePermissionGuard modulePermissionGuard,
                                       UploadRuleService uploadRuleService,
                                       AttachmentRecordAccessService attachmentRecordAccessService) {
        this.attachmentBindingService = attachmentBindingService;
        this.attachmentWebMapper = attachmentWebMapper;
        this.modulePermissionGuard = modulePermissionGuard;
        this.uploadRuleService = uploadRuleService;
        this.attachmentRecordAccessService = attachmentRecordAccessService;
    }

    @GetMapping
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ApiResponse<AttachmentBindingResponse> detail(@AuthenticationPrincipal SecurityPrincipal principal,
                                                         @RequestParam @NotBlank @Size(max = 64) String moduleKey,
                                                         @RequestParam @Positive Long recordId) {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        if (!uploadRuleService.isPageUploadEnabled(normalizedModuleKey)) {
            return ApiResponse.success(attachmentWebMapper.toBindingResponse(normalizedModuleKey, recordId, List.of()));
        }
        attachmentRecordAccessService.assertRecordAccessible(principal, normalizedModuleKey, "read", recordId);
        List<AttachmentView> attachments = attachmentBindingService.list(normalizedModuleKey, recordId);
        return ApiResponse.success(attachmentWebMapper.toBindingResponse(normalizedModuleKey, recordId, attachments));
    }

    @PutMapping
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ApiResponse<AttachmentBindingResponse> update(@AuthenticationPrincipal SecurityPrincipal principal,
                                                         @Valid @RequestBody AttachmentBindingRequest request) {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, request.moduleKey(), "update");
        if (!uploadRuleService.isPageUploadEnabled(normalizedModuleKey)) {
            return ApiResponse.success(
                    "更新成功",
                    attachmentWebMapper.toBindingResponse(normalizedModuleKey, request.recordId(), List.of())
            );
        }
        attachmentRecordAccessService.assertRecordAccessible(principal, normalizedModuleKey, "update", request.recordId());
        List<AttachmentView> attachments = attachmentBindingService.replace(
                normalizedModuleKey,
                request.recordId(),
                request.attachmentIds()
        );
        return ApiResponse.success(
                "更新成功",
                attachmentWebMapper.toBindingResponse(normalizedModuleKey, request.recordId(), attachments)
        );
    }
}
