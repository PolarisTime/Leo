package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.AttachmentWebService;
import com.leo.erp.attachment.web.dto.AttachmentBindingRequest;
import com.leo.erp.attachment.web.dto.AttachmentBindingCountResponse;
import com.leo.erp.attachment.web.dto.AttachmentBindingResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@Validated
@RequestMapping("/attachments/bindings")
public class AttachmentBindingController {

    private final AttachmentWebService attachmentWebService;
    private final ModulePermissionGuard modulePermissionGuard;
    private final AttachmentRecordAccessService attachmentRecordAccessService;

    public AttachmentBindingController(AttachmentWebService attachmentWebService,
                                       ModulePermissionGuard modulePermissionGuard,
                                       AttachmentRecordAccessService attachmentRecordAccessService) {
        this.attachmentWebService = attachmentWebService;
        this.modulePermissionGuard = modulePermissionGuard;
        this.attachmentRecordAccessService = attachmentRecordAccessService;
    }

    @GetMapping
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ApiResponse<AttachmentBindingResponse> detail(@AuthenticationPrincipal SecurityPrincipal principal,
                                                         @RequestParam @NotBlank @Size(max = 64) String moduleKey,
                                                         @RequestParam @Positive Long recordId) {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        attachmentRecordAccessService.assertRecordAccessible(principal, normalizedModuleKey, "read", recordId);
        return ApiResponse.success(attachmentWebService.detail(normalizedModuleKey, recordId));
    }

    @GetMapping("/counts")
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ApiResponse<AttachmentBindingCountResponse> counts(@AuthenticationPrincipal SecurityPrincipal principal,
                                                              @RequestParam @NotBlank @Size(max = 64) String moduleKey,
                                                              @RequestParam @NotBlank String recordIds) {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        List<Long> normalizedRecordIds = parseRecordIds(recordIds);
        List<Long> accessibleRecordIds = new ArrayList<>(normalizedRecordIds.size());
        for (Long recordId : normalizedRecordIds) {
            if (canCountRecordAttachments(principal, normalizedModuleKey, recordId)) {
                accessibleRecordIds.add(recordId);
            }
        }
        return ApiResponse.success(attachmentWebService.counts(normalizedModuleKey, accessibleRecordIds));
    }

    @PutMapping
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ApiResponse<AttachmentBindingResponse> update(@AuthenticationPrincipal SecurityPrincipal principal,
                                                         @Valid @RequestBody AttachmentBindingRequest request) {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, request.moduleKey(), "update");
        attachmentRecordAccessService.assertRecordAccessible(principal, normalizedModuleKey, "update", request.recordId());
        return ApiResponse.success(
                "更新成功",
                attachmentWebService.replace(normalizedModuleKey, request.recordId(), request.attachmentIds())
        );
    }

    private List<Long> parseRecordIds(String recordIds) {
        return Arrays.stream(recordIds.split(","))
                .map(String::trim)
                .filter(item -> item.matches("\\d+"))
                .map(Long::parseLong)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }

    private boolean canCountRecordAttachments(SecurityPrincipal principal, String moduleKey, Long recordId) {
        try {
            attachmentRecordAccessService.assertRecordAccessible(principal, moduleKey, "read", recordId);
            return true;
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.NOT_FOUND) {
                return false;
            }
            throw ex;
        }
    }
}
