package com.leo.erp.system.securitykey.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.securitykey.service.SecurityKeyService;
import com.leo.erp.system.securitykey.web.dto.SecurityKeyOverviewResponse;
import com.leo.erp.system.securitykey.web.dto.SecurityKeyRotateResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityKeyControllerTest {

    private final SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
    private final SecurityKeyController controller = new SecurityKeyController(securityKeyService);

    @Test
    void overviewReturnsSecurityKeyOverview() {
        SecurityKeyOverviewResponse overview = mock(SecurityKeyOverviewResponse.class);
        when(securityKeyService.getOverview()).thenReturn(overview);

        ApiResponse<SecurityKeyOverviewResponse> response = controller.overview();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(overview);
    }

    @Test
    void rotateJwtReturnsRotateResponse() {
        SecurityKeyRotateResponse rotateResponse = mock(SecurityKeyRotateResponse.class);
        when(securityKeyService.rotateJwtMasterKey()).thenReturn(rotateResponse);

        ApiResponse<SecurityKeyRotateResponse> response = controller.rotateJwt();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("JWT 主密钥轮转成功");
        verify(securityKeyService).rotateJwtMasterKey();
    }

    @Test
    void rotateTotpReturnsRotateResponse() {
        SecurityKeyRotateResponse rotateResponse = mock(SecurityKeyRotateResponse.class);
        when(securityKeyService.rotateTotpMasterKey()).thenReturn(rotateResponse);

        ApiResponse<SecurityKeyRotateResponse> response = controller.rotateTotp();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("2FA 主密钥轮转成功");
        verify(securityKeyService).rotateTotpMasterKey();
    }
}
