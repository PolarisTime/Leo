package com.leo.erp.master.material.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.master.material.service.MaterialCategoryService;
import com.leo.erp.master.material.web.dto.MaterialCategoryOptionResponse;
import com.leo.erp.master.material.web.dto.MaterialCategoryRequest;
import com.leo.erp.master.material.web.dto.MaterialCategoryResponse;
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

class MaterialCategoryControllerTest {

    private final MaterialCategoryService service = mock(MaterialCategoryService.class);
    private final MaterialCategoryController controller = new MaterialCategoryController(service);

    @Test
    void pageReturnsPaginatedCategories() {
        MaterialCategoryResponse category = mock(MaterialCategoryResponse.class);
        Page<MaterialCategoryResponse> page = new PageImpl<>(List.of(category));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.page(any(), eq("test"), eq("active"))).thenReturn(page);

        ApiResponse<PageResponse<MaterialCategoryResponse>> response = controller.page(query, "test", "active");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsCategoryById() {
        MaterialCategoryResponse category = mock(MaterialCategoryResponse.class);
        when(service.detail(1L)).thenReturn(category);

        ApiResponse<MaterialCategoryResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(category);
    }

    @Test
    void createReturnsCreatedCategory() {
        MaterialCategoryRequest request = mock(MaterialCategoryRequest.class);
        MaterialCategoryResponse created = mock(MaterialCategoryResponse.class);
        when(service.create(request)).thenReturn(created);

        ApiResponse<MaterialCategoryResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(service).create(request);
    }

    @Test
    void updateReturnsUpdatedCategory() {
        MaterialCategoryRequest request = mock(MaterialCategoryRequest.class);
        MaterialCategoryResponse updated = mock(MaterialCategoryResponse.class);
        when(service.update(1L, request)).thenReturn(updated);

        ApiResponse<MaterialCategoryResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(service).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(service).delete(1L);
    }

    @Test
    void optionsReturnsCategoryOptions() {
        MaterialCategoryOptionResponse option = mock(MaterialCategoryOptionResponse.class);
        when(service.options()).thenReturn(List.of(option));

        ApiResponse<List<MaterialCategoryOptionResponse>> response = controller.options();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(option);
        verify(service).options();
    }
}