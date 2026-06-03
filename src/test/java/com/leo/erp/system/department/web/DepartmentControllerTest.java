package com.leo.erp.system.department.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.system.department.service.DepartmentService;
import com.leo.erp.system.department.web.dto.DepartmentOptionResponse;
import com.leo.erp.system.department.web.dto.DepartmentRequest;
import com.leo.erp.system.department.web.dto.DepartmentResponse;
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

class DepartmentControllerTest {

    private final DepartmentService departmentService = mock(DepartmentService.class);
    private final DepartmentController controller = new DepartmentController(departmentService);

    @Test
    void pageReturnsPaginatedDepartments() {
        DepartmentResponse department = mock(DepartmentResponse.class);
        Page<DepartmentResponse> page = new PageImpl<>(List.of(department));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(departmentService.page(any(), eq("test"), eq("active"))).thenReturn(page);

        ApiResponse<PageResponse<DepartmentResponse>> response = controller.page(query, "test", "active");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void optionsReturnsDepartmentOptions() {
        DepartmentOptionResponse option = mock(DepartmentOptionResponse.class);
        when(departmentService.options()).thenReturn(List.of(option));

        ApiResponse<List<DepartmentOptionResponse>> response = controller.options();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(option);
        verify(departmentService).options();
    }

    @Test
    void detailReturnsDepartmentById() {
        DepartmentResponse department = mock(DepartmentResponse.class);
        when(departmentService.detail(1L)).thenReturn(department);

        ApiResponse<DepartmentResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(department);
    }

    @Test
    void createReturnsCreatedDepartment() {
        DepartmentRequest request = mock(DepartmentRequest.class);
        DepartmentResponse created = mock(DepartmentResponse.class);
        when(departmentService.create(request)).thenReturn(created);

        ApiResponse<DepartmentResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(departmentService).create(request);
    }

    @Test
    void updateReturnsUpdatedDepartment() {
        DepartmentRequest request = mock(DepartmentRequest.class);
        DepartmentResponse updated = mock(DepartmentResponse.class);
        when(departmentService.update(1L, request)).thenReturn(updated);

        ApiResponse<DepartmentResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(departmentService).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(departmentService).delete(1L);
    }
}