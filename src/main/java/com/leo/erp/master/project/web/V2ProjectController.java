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
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/projects")
public class V2ProjectController {

    private final ProjectService projectService;
    private final ProjectPriceRuleService priceRuleService;

    public V2ProjectController(ProjectService projectService,
                               ProjectPriceRuleService priceRuleService) {
        this.projectService = projectService;
        this.priceRuleService = priceRuleService;
    }

    @GetMapping("/options")
    @RequirePermission(PermissionCodes.PROJECTS_READ)
    public List<ProjectOptionResponse> options(@RequestParam Long customerId) {
        return OptionLimits.cap(projectService.listActiveOptions(customerId));
    }

    @GetMapping
    @RequirePermission(PermissionCodes.PROJECTS_READ)
    public PageResponse<ProjectResponse> page(@BindPageQuery(sortFieldKey = "project") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status, @RequestParam(required = false) Long customerId) {
        return PageResponse.from(projectService.page(query, keyword, status, customerId));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.PROJECTS_READ)
    public ProjectResponse detail(@PathVariable Long id) {
        return projectService.detail(id);
    }

    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.PROJECTS_CREATE)
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        return V2ResponseSupport.created("/projects", projectService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.PROJECTS_UPDATE)
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.PROJECTS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return V2ResponseSupport.noContent();
    }

    @Operation(summary = "查询项目价格规定")
    @GetMapping("/{id}/price-rules")
    @RequirePermission(PermissionCodes.PROJECTS_READ)
    public List<ProjectPriceRuleResponse> priceRules(@PathVariable Long id) {
        return priceRuleService.list(id);
    }

    @Operation(summary = "整体替换项目价格规定(新增/更新/软删/重排)")
    @IdempotencyRequired
    @PutMapping("/{id}/price-rules")
    @RequirePermission(PermissionCodes.PROJECTS_UPDATE)
    public List<ProjectPriceRuleResponse> replacePriceRules(
            @PathVariable Long id,
            @Valid @RequestBody List<ProjectPriceRuleRequest> requests) {
        return priceRuleService.replace(id, requests);
    }
}
