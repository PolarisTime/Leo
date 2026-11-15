package com.leo.erp.master.project.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.support.OptionLimits;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.project.service.ProjectService;
import com.leo.erp.master.project.service.ProjectPriceRuleService;
import com.leo.erp.master.project.web.dto.ProjectOptionResponse;
import com.leo.erp.master.project.web.dto.ProjectPriceRuleRequest;
import com.leo.erp.master.project.web.dto.ProjectPriceRuleResponse;
import com.leo.erp.master.project.web.dto.ProjectRequest;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
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
@RequestMapping(ApiVersion.PREFIX + "/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectPriceRuleService priceRuleService;

    public ProjectController(ProjectService projectService,
                               ProjectPriceRuleService priceRuleService) {
        this.projectService = projectService;
        this.priceRuleService = priceRuleService;
    }

    @Operation(summary = "查询项目下拉选项", description = "按客户查询启用状态的项目精简列表，供单据选择使用；数量受 OptionLimits 上限保护，不分页。需 PROJECTS_READ 权限。")
    @GetMapping("/options")
    @RequirePermission(PermissionCodes.PROJECTS_READ)
    public List<ProjectOptionResponse> options(@RequestParam Long customerId) {
        return OptionLimits.cap(projectService.listActiveOptions(customerId));
    }

    @Operation(summary = "分页查询项目", description = "按关键字、状态与客户过滤，分页返回（PageResponse），默认按 project 稳定排序。需 PROJECTS_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.PROJECTS_READ)
    public PageResponse<ProjectResponse> page(@BindPageQuery(sortFieldKey = "project") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status, @RequestParam(required = false) Long customerId) {
        return PageResponse.from(projectService.page(query, keyword, status, customerId));
    }

    @Operation(summary = "查询项目详情", description = "按雪花 ID 查询单个项目（路径 id 以字符串传递）；不存在返回 404。需 PROJECTS_READ 权限。")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.PROJECTS_READ)
    public ProjectResponse detail(@PathVariable @SnowflakeId Long id) {
        return projectService.detail(id);
    }

    @Operation(summary = "创建项目", description = "成功返回 201 与 Location /projects/{id}，响应体为新建项目（id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 PROJECTS_CREATE 权限。")
    @PostMapping
    @Created
    @RequirePermission(PermissionCodes.PROJECTS_CREATE)
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        return ResponseSupport.created("/projects", projectService.create(request));
    }

    @Operation(summary = "更新项目", description = "PUT 整体替换资源，成功返回 200；路径 id 为雪花 ID 字符串。重复提交需 Idempotency-Key（类级强制）。需 PROJECTS_UPDATE 权限。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.PROJECTS_UPDATE)
    public ProjectResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @Operation(summary = "删除项目", description = "幂等删除，成功返回 204 No Content；重复删除结果一致。需 PROJECTS_DELETE 权限。")
    @DeleteMapping("/{id}")
    @NoContent
    @RequirePermission(PermissionCodes.PROJECTS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        projectService.delete(id);
        return ResponseSupport.noContent();
    }

    @Operation(summary = "查询项目价格规定")
    @GetMapping("/{id}/price-rules")
    @RequirePermission(PermissionCodes.PROJECTS_READ)
    public List<ProjectPriceRuleResponse> priceRules(@PathVariable @SnowflakeId Long id) {
        return priceRuleService.list(id);
    }

    @Operation(summary = "整体替换项目价格规定(新增/更新/软删/重排)")
    @IdempotencyRequired
    @PutMapping("/{id}/price-rules")
    @RequirePermission(PermissionCodes.PROJECTS_UPDATE)
    public List<ProjectPriceRuleResponse> replacePriceRules(
            @PathVariable @SnowflakeId Long id,
            @Valid @RequestBody List<ProjectPriceRuleRequest> requests) {
        return priceRuleService.replace(id, requests);
    }
}
