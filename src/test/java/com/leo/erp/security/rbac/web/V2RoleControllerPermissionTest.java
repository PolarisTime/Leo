package com.leo.erp.security.rbac.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.PermissionMethodSecurityConfig;
import com.leo.erp.security.rbac.service.RoleService;
import com.leo.erp.security.rbac.web.dto.RoleDetailResponse;
import java.util.Arrays;
import java.util.List;
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
 * 角色管理端点授权抽查：读/写权限分离、通配放行。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        PermissionMethodSecurityConfig.class,
        V2RoleControllerPermissionTest.TestConfig.class
})
class V2RoleControllerPermissionTest {

    @Autowired
    private V2RoleController controller;

    @Autowired
    private RoleService roleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(roleService);
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
    void detail_withoutReadPermission_shouldReturn403() throws Exception {
        authenticate(PermissionCodes.ROLES_WRITE);

        mockMvc.perform(get("/v2.0/roles/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030));

        verify(roleService, never()).detail(anyLong());
    }

    @Test
    void detail_withReadPermission_shouldSucceed() throws Exception {
        when(roleService.detail(1L)).thenReturn(new RoleDetailResponse(1L, "SUPER_ADMIN", "超级管理员", null, true, "正常", List.of("*")));
        authenticate(PermissionCodes.ROLES_READ);

        mockMvc.perform(get("/v2.0/roles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUPER_ADMIN"));
    }

    @Test
    void delete_withWildcardAuthority_shouldSucceed() throws Exception {
        authenticate(PermissionCodes.WILDCARD);

        mockMvc.perform(delete("/v2.0/roles/9"))
                .andExpect(status().isNoContent());

        verify(roleService).delete(9L);
    }

    @Test
    void delete_withoutWritePermission_shouldReturn403() throws Exception {
        authenticate(PermissionCodes.ROLES_READ);

        mockMvc.perform(delete("/v2.0/roles/9"))
                .andExpect(status().isForbidden());

        verify(roleService, never()).delete(anyLong());
    }

    static void authenticate(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var authentication = new UsernamePasswordAuthenticationToken("tester", "n/a", granted);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Configuration
    static class TestConfig {

        @Bean
        RoleService roleService() {
            return mock(RoleService.class);
        }

        @Bean
        V2RoleController v2RoleController(RoleService roleService) {
            return new V2RoleController(roleService);
        }
    }
}
