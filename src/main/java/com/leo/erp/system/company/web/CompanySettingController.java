package com.leo.erp.system.company.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.PublicAccess;
import org.springframework.security.access.prepost.PreAuthorize;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.company.web.dto.CompanySettingOptionResponse;
import com.leo.erp.system.company.web.dto.CompanySettingRequest;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
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

@RestController
@Validated
@RequestMapping("/company-settings")
public class CompanySettingController {

    private final CompanySettingService companySettingService;

    public CompanySettingController(CompanySettingService companySettingService) {
        this.companySettingService = companySettingService;
    }

    @GetMapping
    @PreAuthorize("@rbac.check('company-setting', 'read')")
    public ApiResponse<PageResponse<CompanySettingResponse>> page(
            @BindPageQuery(sortFieldKey = "company-setting") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(companySettingService.page(query, keyword, status)));
    }

    @GetMapping("/options")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<CompanySettingOptionResponse>> options() {
        return ApiResponse.success(companySettingService.listActiveOptions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbac.check('company-setting', 'read')")
    public ApiResponse<CompanySettingResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(companySettingService.detail(id));
    }

    @GetMapping("/name")
    @PublicAccess
    public ApiResponse<String> companyName() {
        CompanySettingResponse current = companySettingService.current();
        return ApiResponse.success(ErrorCode.SUCCESS.getMessage(), current == null ? "" : current.companyName());
    }

    @GetMapping("/current")
    @PreAuthorize("@rbac.check('company-setting', 'read')")
    public ApiResponse<CompanySettingResponse> current() {
        return ApiResponse.success(companySettingService.current());
    }

    @PutMapping("/current")
    @PreAuthorize("@rbac.check('company-setting', 'update')")
    @OperationLoggable(moduleName = "结算主体", actionType = "保存")
    public ApiResponse<CompanySettingResponse> saveCurrent(@Valid @RequestBody CompanySettingRequest request) {
        return ApiResponse.success("保存成功", companySettingService.saveCurrent(request));
    }

    @PostMapping
    @PreAuthorize("@rbac.check('company-setting', 'create')")
    public ApiResponse<CompanySettingResponse> create(@Valid @RequestBody CompanySettingRequest request) {
        return ApiResponse.success("创建成功", companySettingService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbac.check('company-setting', 'update')")
    public ApiResponse<CompanySettingResponse> update(@PathVariable Long id, @Valid @RequestBody CompanySettingRequest request) {
        return ApiResponse.success("更新成功", companySettingService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.check('company-setting', 'delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        companySettingService.delete(id);
        return ApiResponse.success("删除成功");
    }
}
