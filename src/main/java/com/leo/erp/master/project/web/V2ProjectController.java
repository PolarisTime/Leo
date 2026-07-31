package com.leo.erp.master.project.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.project.service.ProjectService;
import com.leo.erp.master.project.web.dto.ProjectOptionResponse;
import com.leo.erp.master.project.web.dto.ProjectRequest;
import com.leo.erp.master.project.web.dto.ProjectResponse;
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
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/projects")
public class V2ProjectController {

    private final ProjectService projectService;

    public V2ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/options")
    public List<ProjectOptionResponse> options(@RequestParam Long customerId) {
        return projectService.listActiveOptions(customerId);
    }

    @GetMapping
    public PageResponse<ProjectResponse> page(@BindPageQuery(sortFieldKey = "project") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status, @RequestParam(required = false) Long customerId) {
        return PageResponse.from(projectService.page(query, keyword, status, customerId));
    }

    @GetMapping("/{id}")
    public ProjectResponse detail(@PathVariable Long id) {
        return projectService.detail(id);
    }

    @PostMapping
    @V2Created
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        return V2ResponseSupport.created("/projects", projectService.create(request));
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
