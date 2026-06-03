package com.leo.erp.auth.web;

import com.leo.erp.auth.service.UserAccountAdminService;
import com.leo.erp.auth.service.UserAccountPreferenceService;
import com.leo.erp.auth.web.dto.LoginNameAvailabilityResponse;
import com.leo.erp.auth.web.dto.TotpEnableRequest;
import com.leo.erp.auth.web.dto.TotpSetupResponse;
import com.leo.erp.auth.web.dto.UserAccountAdminRequest;
import com.leo.erp.auth.web.dto.UserAccountAdminResponse;
import com.leo.erp.auth.web.dto.UserAccountCreateResponse;
import com.leo.erp.auth.web.dto.UserAccountPreferencesPayload;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.security.support.SecurityPrincipal;
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

class UserAccountAdminControllerTest {

    private final UserAccountAdminService userAccountAdminService = mock(UserAccountAdminService.class);
    private final UserAccountPreferenceService userAccountPreferenceService = mock(UserAccountPreferenceService.class);
    private final UserAccountAdminController controller = new UserAccountAdminController(userAccountAdminService, userAccountPreferenceService);

    @Test
    void pageReturnsPaginatedUsers() {
        UserAccountAdminResponse user = mock(UserAccountAdminResponse.class);
        Page<UserAccountAdminResponse> page = new PageImpl<>(List.of(user));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(userAccountAdminService.page(any(), eq("test"), eq("active"))).thenReturn(page);

        ApiResponse<PageResponse<UserAccountAdminResponse>> response = controller.page(query, "test", "active");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void preferencesReturnsUserPreferences() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        when(principal.id()).thenReturn(1L);
        UserAccountPreferencesPayload preferences = mock(UserAccountPreferencesPayload.class);
        when(userAccountPreferenceService.getPreferences(eq(1L))).thenReturn(preferences);

        ApiResponse<UserAccountPreferencesPayload> response = controller.preferences(principal);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(preferences);
    }

    @Test
    void savePreferencesReturnsSavedPreferences() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        when(principal.id()).thenReturn(1L);
        UserAccountPreferencesPayload request = mock(UserAccountPreferencesPayload.class);
        UserAccountPreferencesPayload saved = mock(UserAccountPreferencesPayload.class);
        when(userAccountPreferenceService.savePreferences(eq(1L), eq(request))).thenReturn(saved);

        ApiResponse<UserAccountPreferencesPayload> response = controller.savePreferences(principal, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("保存成功");
        verify(userAccountPreferenceService).savePreferences(1L, request);
    }

    @Test
    void detailReturnsUserById() {
        UserAccountAdminResponse user = mock(UserAccountAdminResponse.class);
        when(userAccountAdminService.detail(1L)).thenReturn(user);

        ApiResponse<UserAccountAdminResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(user);
    }

    @Test
    void checkLoginNameAvailabilityReturnsAvailability() {
        LoginNameAvailabilityResponse availability = mock(LoginNameAvailabilityResponse.class);
        when(userAccountAdminService.checkLoginNameAvailability(eq("admin"), eq(null))).thenReturn(availability);

        ApiResponse<LoginNameAvailabilityResponse> response = controller.checkLoginNameAvailability("admin", null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(availability);
    }

    @Test
    void createReturnsCreatedUser() {
        UserAccountAdminRequest request = mock(UserAccountAdminRequest.class);
        UserAccountCreateResponse created = mock(UserAccountCreateResponse.class);
        when(userAccountAdminService.create(request)).thenReturn(created);

        ApiResponse<UserAccountCreateResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(userAccountAdminService).create(request);
    }

    @Test
    void updateReturnsUpdatedUser() {
        UserAccountAdminRequest request = mock(UserAccountAdminRequest.class);
        UserAccountAdminResponse updated = mock(UserAccountAdminResponse.class);
        when(userAccountAdminService.update(1L, request)).thenReturn(updated);

        ApiResponse<UserAccountAdminResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(userAccountAdminService).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(userAccountAdminService).delete(1L);
    }

    @Test
    void setup2faReturnsTotpSetupResponse() {
        TotpSetupResponse totpResponse = mock(TotpSetupResponse.class);
        when(userAccountAdminService.setup2fa(1L)).thenReturn(totpResponse);

        ApiResponse<TotpSetupResponse> response = controller.setup2fa(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("密钥生成成功");
        verify(userAccountAdminService).setup2fa(1L);
    }

    @Test
    void enable2faReturnsUpdatedUser() {
        TotpEnableRequest request = mock(TotpEnableRequest.class);
        UserAccountAdminResponse updated = mock(UserAccountAdminResponse.class);
        when(userAccountAdminService.enable2fa(1L, request)).thenReturn(updated);

        ApiResponse<UserAccountAdminResponse> response = controller.enable2fa(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("2FA已启用");
        verify(userAccountAdminService).enable2fa(1L, request);
    }

    @Test
    void disable2faReturnsUpdatedUser() {
        UserAccountAdminResponse updated = mock(UserAccountAdminResponse.class);
        when(userAccountAdminService.disable2fa(1L)).thenReturn(updated);

        ApiResponse<UserAccountAdminResponse> response = controller.disable2fa(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("2FA已禁用");
        verify(userAccountAdminService).disable2fa(1L);
    }
}
