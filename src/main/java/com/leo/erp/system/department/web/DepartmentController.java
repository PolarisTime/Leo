package com.leo.erp.system.department.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import org.springframework.security.access.prepost.PreAuthorize;
import com.leo.erp.system.department.service.DepartmentService;
import com.leo.erp.system.department.web.dto.DepartmentOptionResponse;
import com.leo.erp.system.department.web.dto.DepartmentRequest;
import com.leo.erp.system.department.web.dto.DepartmentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<DepartmentResponse>> page(
            @BindPageQuery(sortFieldKey = "department") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(departmentService.page(query, keyword, status)));
    }

    @GetMapping("/options")
    @PreAuthorize("@rbac.check('department', 'read')")
    public ApiResponse<List<DepartmentOptionResponse>> options() {
        return ApiResponse.success(departmentService.options());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbac.check('department', 'read')")
    public ApiResponse<DepartmentResponse> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(departmentService.detail(id));
    }

    @PostMapping
    @PreAuthorize("@rbac.check('department', 'create')")
    public ApiResponse<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        return ApiResponse.success("创建成功", departmentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbac.check('department', 'update')")
    public ApiResponse<DepartmentResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody DepartmentRequest request) {
        return ApiResponse.success("更新成功", departmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.check('department', 'delete')")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        departmentService.delete(id);
        return ApiResponse.success("删除成功");
    }
}
