package com.leo.erp.common.moduleexport.web;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.moduleexport.service.ModuleExportService;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.PermissionMethodSecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-exports 端点级授权测试：缺少 {@code module-exports:export} 时 403，
 * 持有该权限或资源级通配时放行。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        PermissionMethodSecurityConfig.class,
        V2ModuleExportControllerPermissionTest.TestConfig.class
})
class V2ModuleExportControllerPermissionTest {

    private static final String ENDPOINT = "/v2.0/module-exports";
    private static final String BODY = "{\"moduleKey\":\"sales-order\",\"recordIds\":[\"101\"]}";

    @Autowired
    private V2ModuleExportController controller;

    @Autowired
    private ModuleExportService moduleExportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(moduleExportService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createEndpoint_withoutExportPermission_shouldReturn403() throws Exception {
        authenticate(PermissionCodes.SALES_ORDERS_READ);

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verify(moduleExportService, never()).export(anyString(), any());
    }

    @Test
    void createEndpoint_withModuleExportsExportPermission_shouldSucceed() throws Exception {
        when(moduleExportService.export(anyString(), any())).thenReturn(new FileDownloadResponse(
                "销售订单.xlsx", ModuleExportService.xlsxMediaType(), new byte[]{1, 2, 3}));
        authenticate(PermissionCodes.MODULE_EXPORTS_EXPORT);

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void createEndpoint_withResourceWildcard_shouldSucceed() throws Exception {
        when(moduleExportService.export(anyString(), any())).thenReturn(new FileDownloadResponse(
                "销售订单.xlsx", ModuleExportService.xlsxMediaType(), new byte[]{1}));
        authenticate(PermissionCodes.ofResourceWildcard("module-exports"));

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    static void authenticate(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var authentication = new UsernamePasswordAuthenticationToken("tester", "n/a", granted);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Configuration
    static class TestConfig {

        @Bean
        ModuleExportService moduleExportService() {
            return mock(ModuleExportService.class);
        }

        @Bean
        com.leo.erp.common.export.ExportConcurrencyGuard exportConcurrencyGuard() {
            return new com.leo.erp.common.export.ExportConcurrencyGuard(
                    new com.leo.erp.common.export.ExportConcurrencyProperties());
        }

        @Bean
        V2ModuleExportController v2ModuleExportController(
                ModuleExportService moduleExportService,
                com.leo.erp.common.export.ExportConcurrencyGuard exportConcurrencyGuard) {
            return new V2ModuleExportController(moduleExportService, exportConcurrencyGuard);
        }
    }
}
