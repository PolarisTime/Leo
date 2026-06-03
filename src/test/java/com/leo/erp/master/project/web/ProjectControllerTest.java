package com.leo.erp.master.project.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.master.project.service.ProjectService;
import com.leo.erp.master.project.web.dto.ProjectRequest;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectControllerTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final ProjectController controller = new ProjectController(projectService);

    @Test
    void pageReturnsPaginatedProjects() {
        ProjectResponse project = mock(ProjectResponse.class);
        Page<ProjectResponse> page = new PageImpl<>(List.of(project));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(projectService.page(any(), eq("test"), eq("active"))).thenReturn(page);

        ApiResponse<PageResponse<ProjectResponse>> response = controller.page(query, "test", "active");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsProjectById() {
        ProjectResponse project = mock(ProjectResponse.class);
        when(projectService.detail(1L)).thenReturn(project);

        ApiResponse<ProjectResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(project);
    }

    @Test
    void createReturnsCreatedProject() {
        ProjectRequest request = mock(ProjectRequest.class);
        ProjectResponse created = mock(ProjectResponse.class);
        when(projectService.create(request)).thenReturn(created);

        ApiResponse<ProjectResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(projectService).create(request);
    }

    @Test
    void updateReturnsUpdatedProject() {
        ProjectRequest request = mock(ProjectRequest.class);
        ProjectResponse updated = mock(ProjectResponse.class);
        when(projectService.update(1L, request)).thenReturn(updated);

        ApiResponse<ProjectResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(projectService).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(projectService).delete(1L);
    }
}