package com.leo.erp.sales.returns.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.sales.returns.service.SalesReturnCandidateService;
import com.leo.erp.sales.returns.service.SalesReturnService;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.PermissionMethodSecurityConfig;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 端点级授权集成测试：验证真实控制器在缺少写权限时返回 403、拥有相应权限时正常执行。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        PermissionMethodSecurityConfig.class,
        V2SalesReturnControllerPermissionTest.TestConfig.class
})
class V2SalesReturnControllerPermissionTest {

    @Autowired
    private V2SalesReturnController controller;

    @Autowired
    private SalesReturnService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
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
    void deleteEndpoint_withDeletePermission_shouldSucceed() throws Exception {
        authenticate(PermissionCodes.SALES_RETURNS_DELETE);

        mockMvc.perform(delete("/v2.0/sales-returns/5"))
                .andExpect(status().isNoContent());

        verify(service).delete(5L);
    }

    @Test
    void deleteEndpoint_withoutDeletePermission_shouldReturn403() throws Exception {
        authenticate(PermissionCodes.SALES_RETURNS_READ);

        mockMvc.perform(delete("/v2.0/sales-returns/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030));

        verify(service, never()).delete(anyLong());
    }

    @Test
    void readEndpoint_withReadPermission_shouldSucceed() throws Exception {
        when(service.detail(5L)).thenReturn(null);
        authenticate(PermissionCodes.SALES_RETURNS_READ);

        mockMvc.perform(get("/v2.0/sales-returns/5"))
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
        SalesReturnService salesReturnService() {
            return mock(SalesReturnService.class);
        }

        @Bean
        SalesReturnCandidateService salesReturnCandidateService() {
            return mock(SalesReturnCandidateService.class);
        }

        @Bean
        V2SalesReturnController v2SalesReturnController(SalesReturnService salesReturnService,
                                                        SalesReturnCandidateService candidateService) {
            return new V2SalesReturnController(salesReturnService, candidateService);
        }
    }
}
