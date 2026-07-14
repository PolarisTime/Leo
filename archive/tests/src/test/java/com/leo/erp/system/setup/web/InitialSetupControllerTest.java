package com.leo.erp.system.setup.web;

import com.leo.erp.auth.web.dto.TotpSetupResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.setup.service.InitialSetupCoordinator;
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

    private final InitialSetupCoordinator initialSetupCoordinator = mock(InitialSetupCoordinator.class);
    private final InitialSetupController controller = new InitialSetupController(initialSetupCoordinator);

    @Test
    void statusReturnsSetupStatus() {
        InitialSetupStatusResponse status = mock(InitialSetupStatusResponse.class);
        when(initialSetupCoordinator.status()).thenReturn(status);

        ApiResponse<InitialSetupStatusResponse> response = controller.status();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("获取初始化状态成功");
        assertThat(response.data()).isEqualTo(status);
    }

    @Test
    void initializeReturnsSubmitResponse() {
        InitialSetupSubmitRequest request = mock(InitialSetupSubmitRequest.class);
        InitialSetupSubmitResponse submitResponse = mock(InitialSetupSubmitResponse.class);
        when(initialSetupCoordinator.initialize(request)).thenReturn(submitResponse);

        ApiResponse<InitialSetupSubmitResponse> response = controller.initialize(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("系统首次初始化完成");
        verify(initialSetupCoordinator).initialize(request);
    }

    @Test
    void setupAdminTotpReturnsTotpSetupResponse() {
        InitialSetupTotpSetupRequest request = mock(InitialSetupTotpSetupRequest.class);
        TotpSetupResponse totpResponse = mock(TotpSetupResponse.class);
        when(initialSetupCoordinator.setupAdminTotp(request)).thenReturn(totpResponse);

        ApiResponse<TotpSetupResponse> response = controller.setupAdminTotp(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("管理员 2FA 已生成");
        verify(initialSetupCoordinator).setupAdminTotp(request);
    }

    @Test
    void configureAdminReturnsSubmitResponse() {
        InitialSetupAdminSubmitRequest request = mock(InitialSetupAdminSubmitRequest.class);
        InitialSetupSubmitResponse submitResponse = mock(InitialSetupSubmitResponse.class);
        when(initialSetupCoordinator.configureAdmin(request)).thenReturn(submitResponse);

        ApiResponse<InitialSetupSubmitResponse> response = controller.configureAdmin(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("管理员账号初始化完成");
        verify(initialSetupCoordinator).configureAdmin(request);
    }

    @Test
    void configureCompanyReturnsSubmitResponse() {
        InitialSetupCompanyRequest request = mock(InitialSetupCompanyRequest.class);
        InitialSetupSubmitResponse submitResponse = mock(InitialSetupSubmitResponse.class);
        when(initialSetupCoordinator.configureCompany(request)).thenReturn(submitResponse);

        ApiResponse<InitialSetupSubmitResponse> response = controller.configureCompany(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("公司主体初始化完成");
        verify(initialSetupCoordinator).configureCompany(request);
    }
}
