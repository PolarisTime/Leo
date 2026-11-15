package com.leo.erp.system.company.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.support.OptionLimits;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.company.web.dto.CompanySettingOptionResponse;
import com.leo.erp.system.company.web.dto.CompanySettingRequest;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.api.ResponseSupport;
import com.leo.erp.common.api.Created;
import com.leo.erp.common.api.NoContent;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/company-settings")
public class CompanySettingController {

    private final CompanySettingService companySettingService;

    public CompanySettingController(CompanySettingService companySettingService) {
        this.companySettingService = companySettingService;
    }

    @Operation(summary = "分页查询结算主体", description = "按关键字与状态过滤，分页返回（PageResponse），默认按 company-setting 稳定排序。需 COMPANY_SETTINGS_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.COMPANY_SETTINGS_READ)
    public PageResponse<CompanySettingResponse> page(@BindPageQuery(sortFieldKey = "company-setting") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return PageResponse.from(companySettingService.page(query, keyword, status));
    }

    @Operation(summary = "查询结算主体下拉选项", description = "返回启用状态的结算主体精简列表，供单据抬头选择；数量受 OptionLimits 上限保护，不分页。需 COMPANY_SETTINGS_READ 权限。")
    @GetMapping("/options")
    @RequirePermission(PermissionCodes.COMPANY_SETTINGS_READ)
    public List<CompanySettingOptionResponse> options() {
        return OptionLimits.cap(companySettingService.listActiveOptions());
    }

    @Operation(summary = "查询结算主体详情", description = "按雪花 ID 查询单个结算主体（路径 id 以字符串传递）；不存在返回 404。需 COMPANY_SETTINGS_READ 权限。")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.COMPANY_SETTINGS_READ)
    public CompanySettingResponse detail(@PathVariable @SnowflakeId Long id) {
        return companySettingService.detail(id);
    }

    @Operation(summary = "查询当前结算主体", description = "返回系统当前生效的结算主体配置，供单据与报表抬头使用；未配置时按服务层默认值返回。需 COMPANY_SETTINGS_READ 权限。")
    @GetMapping("/current")
    @RequirePermission(PermissionCodes.COMPANY_SETTINGS_READ)
    public CompanySettingResponse current() {
        return companySettingService.current();
    }

    @Operation(summary = "保存当前结算主体", description = "PUT 按当前生效主体整体保存配置（无路径 id，由服务层 upsert），成功返回 200 与保存后的配置；重复提交结果一致（幂等）。需 COMPANY_SETTINGS_UPDATE 权限。")
    @PutMapping("/current")
    @OperationLoggable(moduleName = "结算主体", actionType = "保存")
    @RequirePermission(PermissionCodes.COMPANY_SETTINGS_UPDATE)
    public CompanySettingResponse saveCurrent(@Valid @RequestBody CompanySettingRequest request) {
        return companySettingService.saveCurrent(request);
    }

    @Operation(summary = "创建结算主体", description = "成功返回 201 与 Location /company-settings/{id}，响应体为新建主体（id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 COMPANY_SETTINGS_CREATE 权限。")
    @PostMapping
    @Created
    @RequirePermission(PermissionCodes.COMPANY_SETTINGS_CREATE)
    public ResponseEntity<CompanySettingResponse> create(@Valid @RequestBody CompanySettingRequest request) {
        return ResponseSupport.created("/company-settings", companySettingService.create(request));
    }

    @Operation(summary = "更新结算主体", description = "PUT 整体替换资源，成功返回 200；路径 id 为雪花 ID 字符串。重复提交需 Idempotency-Key（类级强制）。需 COMPANY_SETTINGS_UPDATE 权限。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.COMPANY_SETTINGS_UPDATE)
    public CompanySettingResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody CompanySettingRequest request) {
        return companySettingService.update(id, request);
    }

    @Operation(summary = "删除结算主体", description = "幂等删除，成功返回 204 No Content；重复删除结果一致。需 COMPANY_SETTINGS_DELETE 权限。")
    @DeleteMapping("/{id}")
    @NoContent
    @RequirePermission(PermissionCodes.COMPANY_SETTINGS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        companySettingService.delete(id);
        return ResponseSupport.noContent();
    }
}
