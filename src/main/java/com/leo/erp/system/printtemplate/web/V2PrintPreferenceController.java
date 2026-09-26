package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.system.printtemplate.service.ProjectPrintPreferenceService;
import com.leo.erp.system.printtemplate.web.dto.ProjectPrintPreferenceRequest;
import com.leo.erp.system.printtemplate.web.dto.ProjectPrintPreferenceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目打印模板偏好资源: 按「项目 + 单据类型」记忆上次所选打印模板。
 * <p>无记忆返回 204; PUT 为幂等新增/更新(project+billType 唯一)。</p>
 */
@Tag(name = "打印模板偏好")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/print-template-preferences")
public class V2PrintPreferenceController {

    private final ProjectPrintPreferenceService preferenceService;

    public V2PrintPreferenceController(ProjectPrintPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @Operation(summary = "查询项目在某单据类型下上次所选打印模板(无记忆返回 204)")
    @GetMapping
    @RequirePermission(PermissionCodes.PRINT_EXPORTS_PRINT)
    public ResponseEntity<ProjectPrintPreferenceResponse> find(
            @RequestParam @Positive Long projectId,
            @RequestParam @NotBlank @Size(max = 64) String billType) {
        return preferenceService.find(projectId, billType)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "记录项目在某单据类型下本次所选打印模板(幂等新增/更新)")
    @PutMapping
    @RequirePermission(PermissionCodes.PRINT_EXPORTS_PRINT)
    public ProjectPrintPreferenceResponse remember(
            @Valid @RequestBody ProjectPrintPreferenceRequest request) {
        return preferenceService.remember(request);
    }
}
