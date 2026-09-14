package com.leo.erp.master.customer.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.master.customer.service.CustomerService;
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
 * 全量铺开后端点级授权抽查：客户资料控制器。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        PermissionMethodSecurityConfig.class,
        V2CustomerControllerPermissionTest.TestConfig.class
})
class V2CustomerControllerPermissionTest {

    @Autowired
    private V2CustomerController controller;

    @Autowired
    private CustomerService service;

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
    void detailEndpoint_withReadPermission_shouldSucceed() throws Exception {
        authenticate(PermissionCodes.CUSTOMERS_READ);

        mockMvc.perform(get("/v2.0/customers/5"))
                .andExpect(status().isOk());
    }

    @Test
    void detailEndpoint_withResourceWildcard_shouldSucceed() throws Exception {
        authenticate(PermissionCodes.ofResourceWildcard("customers"));

        mockMvc.perform(get("/v2.0/customers/5"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteEndpoint_withoutDeletePermission_shouldReturn403() throws Exception {
        authenticate(PermissionCodes.CUSTOMERS_READ);

        mockMvc.perform(delete("/v2.0/customers/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030));

        verify(service, never()).delete(anyLong());
    }

    @Test
    void deleteEndpoint_withDeletePermission_shouldSucceed() throws Exception {
        authenticate(PermissionCodes.CUSTOMERS_DELETE);

        mockMvc.perform(delete("/v2.0/customers/5"))
                .andExpect(status().isNoContent());

        verify(service).delete(5L);
    }

    static void authenticate(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var authentication = new UsernamePasswordAuthenticationToken("tester", "n/a", granted);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Configuration
    static class TestConfig {

        @Bean
        org.springframework.cache.CacheManager cacheManager() {
            return mock(org.springframework.cache.CacheManager.class);
        }

        @Bean
        CustomerService customerService() {
            return mock(CustomerService.class);
        }

        @Bean
        V2CustomerController v2CustomerController(CustomerService customerService) {
            return new V2CustomerController(customerService);
        }
    }
}
