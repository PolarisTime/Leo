package com.leo.erp.system.company.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.company.web.dto.CompanySettingRequest;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/company-settings")
public class CompanySettingController {

    private final CompanySettingService companySettingService;

    public CompanySettingController(CompanySettingService companySettingService) {
        this.companySettingService = companySettingService;
    }

    @GetMapping
    @RequiresPermission(resource = "company-setting", action = "read")
    public ApiResponse<PageResponse<CompanySettingResponse>> page(
            @BindPageQuery(sortFieldKey = "company-settings") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(companySettingService.page(query, keyword, status)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "company-setting", action = "read")
    public ApiResponse<CompanySettingResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(companySettingService.detail(id));
    }

    @GetMapping("/current")
    @RequiresPermission(resource = "company-setting", action = "read")
    public ApiResponse<CompanySettingResponse> current() {
        return ApiResponse.success(companySettingService.current());
    }

    @PutMapping("/current")
    @RequiresPermission(resource = "company-setting", action = "update")
    @OperationLoggable(moduleName = "公司信息", actionType = "保存")
    public ApiResponse<CompanySettingResponse> saveCurrent(@Valid @RequestBody CompanySettingRequest request) {
        return ApiResponse.success("保存成功", companySettingService.saveCurrent(request));
    }

    @PostMapping
    @RequiresPermission(resource = "company-setting", action = "create")
    public ApiResponse<CompanySettingResponse> create(@Valid @RequestBody CompanySettingRequest request) {
        return ApiResponse.success("创建成功", companySettingService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "company-setting", action = "update")
    public ApiResponse<CompanySettingResponse> update(@PathVariable Long id, @Valid @RequestBody CompanySettingRequest request) {
        return ApiResponse.success("更新成功", companySettingService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "company-setting", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        companySettingService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
