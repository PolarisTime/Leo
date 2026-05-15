package com.leo.erp.finance.projectar.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.finance.projectar.service.ProjectArService;
import com.leo.erp.finance.projectar.web.dto.ProjectArDetailRowResponse;
import com.leo.erp.finance.projectar.web.dto.ProjectArSummaryResponse;
import com.leo.erp.security.permission.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/project-ar")
@Tag(name = "项目应收")
public class ProjectArController {

    private final ProjectArService projectArService;

    public ProjectArController(ProjectArService projectArService) {
        this.projectArService = projectArService;
    }

    @GetMapping("/summary")
    @RequiresPermission(resource = "project-ar", action = "read")
    @Operation(summary = "项目应收汇总列表")
    public ApiResponse<PageResponse<ProjectArSummaryResponse>> pageSummary(
            @BindPageQuery(sortFieldKey = "project-ar", directionParam = "sortDirection") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long projectId
    ) {
        return ApiResponse.success(PageResponse.from(projectArService.pageSummary(query, keyword, projectId)));
    }

    @GetMapping("/{projectId}/unreconciled")
    @RequiresPermission(resource = "project-ar", action = "read")
    @Operation(summary = "项目未对账单据列表")
    public ApiResponse<PageResponse<ProjectArDetailRowResponse>> pageUnreconciled(
            @PathVariable Long projectId,
            @BindPageQuery(sortFieldKey = "project-ar-detail", directionParam = "sortDirection") PageQuery query
    ) {
        return ApiResponse.success(PageResponse.from(projectArService.pageUnreconciled(projectId, query)));
    }

    @GetMapping("/{projectId}/reconciled")
    @RequiresPermission(resource = "project-ar", action = "read")
    @Operation(summary = "项目已对账单据列表")
    public ApiResponse<PageResponse<ProjectArDetailRowResponse>> pageReconciled(
            @PathVariable Long projectId,
            @BindPageQuery(sortFieldKey = "project-ar-detail", directionParam = "sortDirection") PageQuery query
    ) {
        return ApiResponse.success(PageResponse.from(projectArService.pageReconciled(projectId, query)));
    }
}
