package com.leo.erp.auth.web;

import com.leo.erp.auth.service.AccountSecurityService;
import com.leo.erp.auth.web.dto.ChangeOwnPasswordRequest;
import com.leo.erp.auth.web.dto.CurrentUserSecurityResponse;
import com.leo.erp.auth.web.dto.TotpEnableRequest;
import com.leo.erp.auth.web.dto.TotpSetupResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountSecurityControllerTest {

    private final AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
    private final AccountSecurityController controller = new AccountSecurityController(accountSecurityService);

    @Test
    void statusReturnsSecurityStatus() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        when(principal.id()).thenReturn(1L);
        CurrentUserSecurityResponse status = mock(CurrentUserSecurityResponse.class);
        when(accountSecurityService.getStatus(eq(1L))).thenReturn(status);

        ApiResponse<CurrentUserSecurityResponse> response = controller.status(principal);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(status);
    }

    @Test
    void changePasswordCallsService() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        when(principal.id()).thenReturn(1L);
        ChangeOwnPasswordRequest request = mock(ChangeOwnPasswordRequest.class);

        ApiResponse<Void> response = controller.changePassword(principal, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("密码修改成功");
        verify(accountSecurityService).changePassword(1L, request);
    }

    @Test
    void setup2faReturnsTotpSetupResponse() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        when(principal.id()).thenReturn(1L);
        TotpSetupResponse totpResponse = mock(TotpSetupResponse.class);
        when(accountSecurityService.setup2fa(eq(1L))).thenReturn(totpResponse);

        ApiResponse<TotpSetupResponse> response = controller.setup2fa(principal);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("密钥生成成功");
        verify(accountSecurityService).setup2fa(1L);
    }

    @Test
    void enable2faReturnsUpdatedStatus() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        when(principal.id()).thenReturn(1L);
        TotpEnableRequest request = mock(TotpEnableRequest.class);
        CurrentUserSecurityResponse updated = mock(CurrentUserSecurityResponse.class);
        when(accountSecurityService.enable2fa(eq(1L), eq(request))).thenReturn(updated);

        ApiResponse<CurrentUserSecurityResponse> response = controller.enable2fa(principal, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("2FA已启用");
        verify(accountSecurityService).enable2fa(1L, request);
    }

    @Test
    void disable2faReturnsUpdatedStatus() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        when(principal.id()).thenReturn(1L);
        CurrentUserSecurityResponse updated = mock(CurrentUserSecurityResponse.class);
        when(accountSecurityService.disable2fa(eq(1L))).thenReturn(updated);

        ApiResponse<CurrentUserSecurityResponse> response = controller.disable2fa(principal);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("2FA已禁用");
        verify(accountSecurityService).disable2fa(1L);
    }
}
