package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.attachment.service.AttachmentWebService;
import com.leo.erp.attachment.web.dto.AttachmentBindingRequest;
import com.leo.erp.attachment.web.dto.AttachmentBindingCountResponse;
import com.leo.erp.attachment.web.dto.AttachmentBindingResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/attachments/bindings")
public class AttachmentBindingController {

    private final AttachmentWebService attachmentWebService;
    private final AttachmentRecordAccessService attachmentRecordAccessService;

    public AttachmentBindingController(AttachmentWebService attachmentWebService,
                                         AttachmentRecordAccessService attachmentRecordAccessService) {
        this.attachmentWebService = attachmentWebService;
        this.attachmentRecordAccessService = attachmentRecordAccessService;
    }

    @Operation(summary = "查询记录已绑定附件", description = "按 moduleKey 与 recordId（雪花 ID 字符串）返回该记录的附件绑定关系；记录不存在返回 404。需 ATTACHMENTS_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.ATTACHMENTS_READ)
    public AttachmentBindingResponse detail(@RequestParam @NotBlank @Size(max = 64) String moduleKey, @RequestParam @Positive Long recordId) {
        String normalizedModuleKey = attachmentRecordAccessService.normalizeModuleKey(moduleKey);
        attachmentRecordAccessService.assertRecordExists(normalizedModuleKey, recordId);
        return attachmentWebService.detail(normalizedModuleKey, recordId);
    }

    @Operation(summary = "批量统计记录附件数量", description = "recordIds 为逗号分隔的雪花 ID 列表，返回每条记录的附件数量；当前用户不可见或不存在的记录静默跳过。需 ATTACHMENTS_READ 权限。")
    @GetMapping("/counts")
    @RequirePermission(PermissionCodes.ATTACHMENTS_READ)
    public AttachmentBindingCountResponse counts(@RequestParam @NotBlank @Size(max = 64) String moduleKey, @RequestParam @NotBlank String recordIds) {
        String normalizedModuleKey = attachmentRecordAccessService.normalizeModuleKey(moduleKey);
        List<Long> normalizedRecordIds = parseRecordIds(recordIds);
        List<Long> accessibleRecordIds = new ArrayList<>(normalizedRecordIds.size());
        for (Long recordId : normalizedRecordIds) {
            if (canCountRecordAttachments(normalizedModuleKey, recordId)) {
                accessibleRecordIds.add(recordId);
            }
        }
        return attachmentWebService.counts(normalizedModuleKey, accessibleRecordIds);
    }

    @Operation(summary = "整体替换记录附件绑定", description = "PUT 用 attachmentIds 全量替换指定记录的附件绑定；记录不存在返回 404。重复提交需 Idempotency-Key（类级强制）。需 ATTACHMENTS_UPDATE 权限。")
    @PutMapping
    @RequirePermission(PermissionCodes.ATTACHMENTS_UPDATE)
    public AttachmentBindingResponse update(@Valid @RequestBody AttachmentBindingRequest request) {
        String normalizedModuleKey = attachmentRecordAccessService.normalizeModuleKey(request.moduleKey());
        attachmentRecordAccessService.assertRecordExists(normalizedModuleKey, request.recordId());
        return attachmentWebService.replace(normalizedModuleKey, request.recordId(), request.attachmentIds());
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

    private boolean canCountRecordAttachments(String moduleKey, Long recordId) {
        try {
            attachmentRecordAccessService.assertRecordExists(moduleKey, recordId);
            return true;
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.NOT_FOUND) {
                return false;
            }
            throw ex;
        }
    }
}
