package com.leo.erp.auth.web;

import com.leo.erp.auth.service.ApiKeyAdminService;
import com.leo.erp.auth.web.dto.ApiKeyActionOptionResponse;
import com.leo.erp.auth.web.dto.ApiKeyRequest;
import com.leo.erp.auth.web.dto.ApiKeyResourceOptionResponse;
import com.leo.erp.auth.web.dto.ApiKeyResponse;
import com.leo.erp.auth.web.dto.ApiKeyUserOptionResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
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

class ApiKeyAdminControllerTest {

    private final ApiKeyAdminService service = mock(ApiKeyAdminService.class);
    private final ApiKeyAdminController controller = new ApiKeyAdminController(service);

    @Test
    void pageReturnsPaginatedApiKeys() {
        ApiKeyResponse apiKey = mock(ApiKeyResponse.class);
        Page<ApiKeyResponse> page = new PageImpl<>(List.of(apiKey));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.page(any(), eq("test"), eq(1L), eq("active"), eq("internal"))).thenReturn(page);

        ApiResponse<PageResponse<ApiKeyResponse>> response = controller.page(query, "test", 1L, "active", "internal");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsApiKeyById() {
        ApiKeyResponse apiKey = mock(ApiKeyResponse.class);
        when(service.detail(1L)).thenReturn(apiKey);

        ApiResponse<ApiKeyResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(apiKey);
    }

    @Test
    void userOptionsReturnsUsers() {
        ApiKeyUserOptionResponse user = mock(ApiKeyUserOptionResponse.class);
        when(service.listAvailableUsers(eq("test"))).thenReturn(List.of(user));

        ApiResponse<List<ApiKeyUserOptionResponse>> response = controller.userOptions("test");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void resourceOptionsReturnsResources() {
        ApiKeyResourceOptionResponse resource = mock(ApiKeyResourceOptionResponse.class);
        when(service.listResourceOptions()).thenReturn(List.of(resource));

        ApiResponse<List<ApiKeyResourceOptionResponse>> response = controller.resourceOptions();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void actionOptionsReturnsActions() {
        ApiKeyActionOptionResponse action = mock(ApiKeyActionOptionResponse.class);
        when(service.listActionOptions()).thenReturn(List.of(action));

        ApiResponse<List<ApiKeyActionOptionResponse>> response = controller.actionOptions();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void generateReturnsGeneratedApiKey() {
        ApiKeyRequest request = mock(ApiKeyRequest.class);
        ApiKeyResponse generated = mock(ApiKeyResponse.class);
        when(service.generate(eq(1L), eq(request))).thenReturn(generated);

        ApiResponse<ApiKeyResponse> response = controller.generate(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("API Key 已生成");
        verify(service).generate(1L, request);
    }

    @Test
    void revokeCallsServiceRevoke() {
        ApiResponse<Void> response = controller.revoke(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("已禁用");
        verify(service).revoke(1L);
    }
}
