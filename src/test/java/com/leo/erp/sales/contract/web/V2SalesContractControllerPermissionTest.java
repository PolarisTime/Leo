package com.leo.erp.sales.contract.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.sales.contract.SalesContractProperties;
import com.leo.erp.sales.contract.service.SalesContractService;
import com.leo.erp.sales.contract.web.dto.SalesContractResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.PermissionMethodSecurityConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * 端点级授权集成测试：验证真实控制器在缺少对应权限时返回 403、拥有权限时正常执行。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        PermissionMethodSecurityConfig.class,
        V2SalesContractControllerPermissionTest.TestConfig.class
})
class V2SalesContractControllerPermissionTest {

    private static final String VALID_BODY = "{"
            + "\"name\":\"年度钢材采购合同\","
            + "\"customerId\":\"1\","
            + "\"projectId\":\"2\","
            + "\"signDate\":\"2026-09-17\","
            + "\"totalAmount\":100.00,"
            + "\"totalTonnage\":1.5"
            + "}";

    @Autowired
    private V2SalesContractController controller;

    @Autowired
    private SalesContractService service;

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
    void readEndpoint_withReadPermission_shouldSucceed() throws Exception {
        when(service.detail(5L)).thenReturn(response());
        authenticate(PermissionCodes.SALES_CONTRACTS_READ);

        mockMvc.perform(get("/v2.0/sales-contracts/5"))
                .andExpect(status().isOk());
    }

    @Test
    void readEndpoint_withoutReadPermission_shouldReturn403() throws Exception {
        authenticate(PermissionCodes.SALES_CONTRACTS_UPDATE);

        mockMvc.perform(get("/v2.0/sales-contracts/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030));
    }

    @Test
    void createEndpoint_withCreatePermission_shouldReturn201() throws Exception {
        when(service.create(any())).thenReturn(response());
        authenticate(PermissionCodes.SALES_CONTRACTS_CREATE);

        mockMvc.perform(post("/v2.0/sales-contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void createEndpoint_withoutCreatePermission_shouldReturn403() throws Exception {
        authenticate(PermissionCodes.SALES_CONTRACTS_READ);

        mockMvc.perform(post("/v2.0/sales-contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verify(service, never()).create(any());
    }

    @Test
    void deleteEndpoint_withDeletePermission_shouldSucceed() throws Exception {
        authenticate(PermissionCodes.SALES_CONTRACTS_DELETE);

        mockMvc.perform(delete("/v2.0/sales-contracts/5"))
                .andExpect(status().isNoContent());

        verify(service).delete(5L);
    }

    @Test
    void deleteEndpoint_withoutDeletePermission_shouldReturn403() throws Exception {
        authenticate(PermissionCodes.SALES_CONTRACTS_READ);

        mockMvc.perform(delete("/v2.0/sales-contracts/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030));

        verify(service, never()).delete(anyLong());
    }

    @Test
    void updateStatusEndpoint_withoutUpdatePermission_shouldReturn403() throws Exception {
        authenticate(PermissionCodes.SALES_CONTRACTS_READ);

        mockMvc.perform(patch("/v2.0/sales-contracts/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"已审核\"}"))
                .andExpect(status().isForbidden());

        verify(service, never()).updateStatus(anyLong(), any());
    }

    private SalesContractResponse response() {
        return new SalesContractResponse(
                100L, "HT-1", "年度钢材采购合同",
                1L, "客户甲", 2L, "项目乙",
                LocalDate.of(2026, 9, 17), null, null,
                new BigDecimal("100.00"), new BigDecimal("1.50000000"),
                "草稿", null, null, null, 0L);
    }

    static void authenticate(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var authentication = new UsernamePasswordAuthenticationToken("tester", "n/a", granted);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Configuration
    static class TestConfig {

        @Bean
        SalesContractService salesContractService() {
            return mock(SalesContractService.class);
        }

        @Bean
        V2SalesContractController v2SalesContractController(SalesContractService salesContractService) {
            return new V2SalesContractController(salesContractService, new SalesContractProperties());
        }
    }
}
