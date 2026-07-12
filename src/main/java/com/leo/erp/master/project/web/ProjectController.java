package com.leo.erp.master.project.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.project.service.ProjectService;
import com.leo.erp.master.project.web.dto.ProjectOptionResponse;
import com.leo.erp.master.project.web.dto.ProjectRequest;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import com.leo.erp.security.permission.RequiresPermission;
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
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/options")
    @RequiresPermission(resource = "project", action = "read")
    public ApiResponse<List<ProjectOptionResponse>> options(@RequestParam Long customerId) {
        return ApiResponse.success(projectService.listActiveOptions(customerId));
    }

    @GetMapping
    @RequiresPermission(resource = "project", action = "read")
    public ApiResponse<PageResponse<ProjectResponse>> page(
            @BindPageQuery(sortFieldKey = "project") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(projectService.page(query, keyword, status)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "project", action = "read")
    public ApiResponse<ProjectResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(projectService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "project", action = "create")
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        return ApiResponse.success("创建成功", projectService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "project", action = "update")
    public ApiResponse<ProjectResponse> update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return ApiResponse.success("更新成功", projectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "project", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.success("删除成功");
    }
}
