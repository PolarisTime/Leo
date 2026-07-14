package com.leo.erp.system.company.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.company.web.dto.CompanySettingOptionResponse;
import com.leo.erp.system.company.web.dto.CompanySettingRequest;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
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

class CompanySettingControllerTest {

    private final CompanySettingService companySettingService = mock(CompanySettingService.class);
    private final CompanySettingController controller = new CompanySettingController(companySettingService);

    @Test
    void pageReturnsPaginatedCompanySettings() {
        CompanySettingResponse setting = mock(CompanySettingResponse.class);
        Page<CompanySettingResponse> page = new PageImpl<>(List.of(setting));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(companySettingService.page(any(), eq("test"), eq("active"))).thenReturn(page);

        ApiResponse<PageResponse<CompanySettingResponse>> response = controller.page(query, "test", "active");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsCompanySettingById() {
        CompanySettingResponse setting = mock(CompanySettingResponse.class);
        when(companySettingService.detail(1L)).thenReturn(setting);

        ApiResponse<CompanySettingResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(setting);
    }

    @Test
    void optionsReturnsActiveCompanySettings() {
        var option = new CompanySettingOptionResponse(1L, "Test Company");
        when(companySettingService.listActiveOptions()).thenReturn(List.of(option));

        ApiResponse<List<CompanySettingOptionResponse>> response = controller.options();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(option);
        verify(companySettingService).listActiveOptions();
    }

    @Test
    void companyNameReturnsCompanyName() {
        CompanySettingResponse setting = mock(CompanySettingResponse.class);
        when(setting.companyName()).thenReturn("Test Company");
        when(companySettingService.current()).thenReturn(setting);

        ApiResponse<String> response = controller.companyName();

        assertThat(response.code()).isEqualTo(0);
        verify(companySettingService).current();
    }

    @Test
    void companyNameReturnsEmptyWhenCurrentIsMissing() {
        when(companySettingService.current()).thenReturn(null);

        ApiResponse<String> response = controller.companyName();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEmpty();
        verify(companySettingService).current();
    }

    @Test
    void currentReturnsCurrentCompanySetting() {
        CompanySettingResponse setting = mock(CompanySettingResponse.class);
        when(companySettingService.current()).thenReturn(setting);

        ApiResponse<CompanySettingResponse> response = controller.current();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(setting);
        verify(companySettingService).current();
    }

    @Test
    void saveCurrentReturnsSavedCompanySetting() {
        CompanySettingRequest request = mock(CompanySettingRequest.class);
        CompanySettingResponse saved = mock(CompanySettingResponse.class);
        when(companySettingService.saveCurrent(request)).thenReturn(saved);

        ApiResponse<CompanySettingResponse> response = controller.saveCurrent(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("保存成功");
        verify(companySettingService).saveCurrent(request);
    }

    @Test
    void createReturnsCreatedCompanySetting() {
        CompanySettingRequest request = mock(CompanySettingRequest.class);
        CompanySettingResponse created = mock(CompanySettingResponse.class);
        when(companySettingService.create(request)).thenReturn(created);

        ApiResponse<CompanySettingResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(companySettingService).create(request);
    }

    @Test
    void updateReturnsUpdatedCompanySetting() {
        CompanySettingRequest request = mock(CompanySettingRequest.class);
        CompanySettingResponse updated = mock(CompanySettingResponse.class);
        when(companySettingService.update(1L, request)).thenReturn(updated);

        ApiResponse<CompanySettingResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(companySettingService).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(companySettingService).delete(1L);
    }
}
