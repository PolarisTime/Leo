package com.leo.erp.system.norule.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.norule.service.GeneralSettingQueryService;
import com.leo.erp.system.norule.service.NoRuleService;
import com.leo.erp.system.norule.web.dto.GeneralSettingResponse;
import com.leo.erp.system.norule.web.dto.NoRuleGenerateResponse;
import com.leo.erp.system.norule.web.dto.NoRuleRequest;
import com.leo.erp.system.norule.web.dto.NoRuleResponse;
import com.leo.erp.system.norule.web.dto.StatementGeneratorRulesResponse;
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

class NoRuleControllerTest {

    private final NoRuleService noRuleService = mock(NoRuleService.class);
    private final GeneralSettingQueryService generalSettingQueryService = mock(GeneralSettingQueryService.class);
    private final ModulePermissionGuard modulePermissionGuard = mock(ModulePermissionGuard.class);
    private final NoRuleController controller = new NoRuleController(noRuleService, generalSettingQueryService, modulePermissionGuard);

    @Test
    void pageReturnsPaginatedSettings() {
        GeneralSettingResponse item = mock(GeneralSettingResponse.class);
        Page<GeneralSettingResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(generalSettingQueryService.page(any(), eq("test"), eq("active"))).thenReturn(page);

        ApiResponse<PageResponse<GeneralSettingResponse>> response = controller.page(query, "test", "active");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void displaySwitchesReturnsList() {
        GeneralSettingResponse item = mock(GeneralSettingResponse.class);
        when(generalSettingQueryService.publicDisplaySwitches()).thenReturn(List.of(item));

        ApiResponse<List<GeneralSettingResponse>> response = controller.displaySwitches();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void clientSettingsReturnsList() {
        GeneralSettingResponse item = mock(GeneralSettingResponse.class);
        when(generalSettingQueryService.publicClientSettings()).thenReturn(List.of(item));

        ApiResponse<List<GeneralSettingResponse>> response = controller.clientSettings();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void statementGeneratorRulesReturnsRules() {
        StatementGeneratorRulesResponse rules = mock(StatementGeneratorRulesResponse.class);
        when(noRuleService.statementGeneratorRules()).thenReturn(rules);

        ApiResponse<StatementGeneratorRulesResponse> response = controller.statementGeneratorRules();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(rules);
    }

    @Test
    void detailReturnsSettingById() {
        NoRuleResponse setting = mock(NoRuleResponse.class);
        when(noRuleService.detail(1L)).thenReturn(setting);

        ApiResponse<NoRuleResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(setting);
    }

    @Test
    void nextNumberReturnsGeneratedNumber() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        NoRuleGenerateResponse generated = mock(NoRuleGenerateResponse.class);
        when(modulePermissionGuard.requirePermission(principal, "sales-order", "create")).thenReturn("sales-order");
        when(noRuleService.nextNumber(eq("sales-order"), eq(principal))).thenReturn(generated);

        ApiResponse<NoRuleGenerateResponse> response = controller.nextNumber(principal, "sales-order");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(generated);
    }

    @Test
    void createReturnsCreatedSetting() {
        NoRuleRequest request = mock(NoRuleRequest.class);
        NoRuleResponse created = mock(NoRuleResponse.class);
        when(noRuleService.create(request)).thenReturn(created);

        ApiResponse<NoRuleResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(noRuleService).create(request);
    }

    @Test
    void updateReturnsUpdatedSetting() {
        NoRuleRequest request = mock(NoRuleRequest.class);
        NoRuleResponse updated = mock(NoRuleResponse.class);
        when(noRuleService.update(1L, request)).thenReturn(updated);

        ApiResponse<NoRuleResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(noRuleService).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(noRuleService).delete(1L);
    }
}
