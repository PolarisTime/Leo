package com.leo.erp.system.setup.web;

import com.leo.erp.auth.web.dto.TotpSetupResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.setup.service.InitialSetupService;
import com.leo.erp.system.setup.web.dto.InitialSetupAdminSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupCompanyRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupStatusResponse;
import com.leo.erp.system.setup.web.dto.InitialSetupSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupSubmitResponse;
import com.leo.erp.system.setup.web.dto.InitialSetupTotpSetupRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialSetupControllerTest {

    private final InitialSetupService initialSetupService = mock(InitialSetupService.class);
    private final InitialSetupController controller = new InitialSetupController(initialSetupService);

    @Test
    void statusReturnsSetupStatus() {
        InitialSetupStatusResponse status = mock(InitialSetupStatusResponse.class);
        when(initialSetupService.status()).thenReturn(status);

        ApiResponse<InitialSetupStatusResponse> response = controller.status();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("获取初始化状态成功");
        assertThat(response.data()).isEqualTo(status);
    }

    @Test
    void initializeReturnsSubmitResponse() {
        InitialSetupSubmitRequest request = mock(InitialSetupSubmitRequest.class);
        InitialSetupSubmitResponse submitResponse = mock(InitialSetupSubmitResponse.class);
        when(initialSetupService.initialize(request)).thenReturn(submitResponse);

        ApiResponse<InitialSetupSubmitResponse> response = controller.initialize(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("系统首次初始化完成");
        verify(initialSetupService).initialize(request);
    }

    @Test
    void setupAdminTotpReturnsTotpSetupResponse() {
        InitialSetupTotpSetupRequest request = mock(InitialSetupTotpSetupRequest.class);
        TotpSetupResponse totpResponse = mock(TotpSetupResponse.class);
        when(initialSetupService.setupAdminTotp(request)).thenReturn(totpResponse);

        ApiResponse<TotpSetupResponse> response = controller.setupAdminTotp(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("管理员 2FA 已生成");
        verify(initialSetupService).setupAdminTotp(request);
    }

    @Test
    void configureAdminReturnsSubmitResponse() {
        InitialSetupAdminSubmitRequest request = mock(InitialSetupAdminSubmitRequest.class);
        InitialSetupSubmitResponse submitResponse = mock(InitialSetupSubmitResponse.class);
        when(initialSetupService.configureAdmin(request)).thenReturn(submitResponse);

        ApiResponse<InitialSetupSubmitResponse> response = controller.configureAdmin(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("管理员账号初始化完成");
        verify(initialSetupService).configureAdmin(request);
    }

    @Test
    void configureCompanyReturnsSubmitResponse() {
        InitialSetupCompanyRequest request = mock(InitialSetupCompanyRequest.class);
        InitialSetupSubmitResponse submitResponse = mock(InitialSetupSubmitResponse.class);
        when(initialSetupService.configureCompany(request)).thenReturn(submitResponse);

        ApiResponse<InitialSetupSubmitResponse> response = controller.configureCompany(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("公司主体初始化完成");
        verify(initialSetupService).configureCompany(request);
    }
}
