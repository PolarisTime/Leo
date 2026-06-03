package com.leo.erp.system.permission.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.system.permission.service.PermissionEntryService;
import com.leo.erp.system.permission.web.dto.CatalogEntryResponse;
import com.leo.erp.system.permission.web.dto.PermissionEntryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionEntryControllerTest {

    private final PermissionEntryService permissionEntryService = mock(PermissionEntryService.class);
    private final PermissionEntryController controller = new PermissionEntryController(permissionEntryService);

    @Test
    void catalogReturnsCatalogEntries() {
        CatalogEntryResponse item = mock(CatalogEntryResponse.class);
        when(permissionEntryService.catalog()).thenReturn(List.of(item));

        ApiResponse<List<CatalogEntryResponse>> response = controller.catalog();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void pageReturnsPaginatedPermissions() {
        PermissionEntryResponse item = mock(PermissionEntryResponse.class);
        Page<PermissionEntryResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(permissionEntryService.page(any(), eq("test"))).thenReturn(page);

        ApiResponse<PageResponse<PermissionEntryResponse>> response = controller.page(query, "test");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsPermissionById() {
        PermissionEntryResponse permission = mock(PermissionEntryResponse.class);
        when(permissionEntryService.detail(1L)).thenReturn(permission);

        ApiResponse<PermissionEntryResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(permission);
    }
}
