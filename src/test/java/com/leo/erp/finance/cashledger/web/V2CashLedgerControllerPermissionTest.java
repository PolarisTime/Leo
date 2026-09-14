package com.leo.erp.finance.cashledger.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.finance.cashledger.service.CashLedgerService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 全量铺开后端点级授权抽查：资金流水控制器（GET 导出动作独立授权）。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        PermissionMethodSecurityConfig.class,
        V2CashLedgerControllerPermissionTest.TestConfig.class
})
class V2CashLedgerControllerPermissionTest {

    @Autowired
    private V2CashLedgerController controller;

    @Autowired
    private CashLedgerService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(service);
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
    void exportEndpoint_withoutExportPermission_shouldReturn403() throws Exception {
        authenticate(PermissionCodes.CASH_LEDGER_READ);

        mockMvc.perform(get("/v2.0/cash-ledger/export").param("settlementCompanyId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030));

        verify(service, never()).exportExcel(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void exportEndpoint_withExportPermission_shouldSucceed() throws Exception {
        when(service.exportExcel(anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FileDownloadResponse(
                        "cash-ledger.xlsx", MediaType.APPLICATION_OCTET_STREAM, new byte[]{1, 2, 3}));
        authenticate(PermissionCodes.CASH_LEDGER_EXPORT);

        mockMvc.perform(get("/v2.0/cash-ledger/export").param("settlementCompanyId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void exportEndpoint_withResourceWildcard_shouldSucceed() throws Exception {
        when(service.exportExcel(anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FileDownloadResponse(
                        "cash-ledger.xlsx", MediaType.APPLICATION_OCTET_STREAM, new byte[]{1}));
        authenticate(PermissionCodes.ofResourceWildcard("cash-ledger"));

        mockMvc.perform(get("/v2.0/cash-ledger/export").param("settlementCompanyId", "1"))
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
        CashLedgerService cashLedgerService() {
            return mock(CashLedgerService.class);
        }

        @Bean
        V2CashLedgerController v2CashLedgerController(CashLedgerService cashLedgerService) {
            return new V2CashLedgerController(cashLedgerService);
        }
    }
}
