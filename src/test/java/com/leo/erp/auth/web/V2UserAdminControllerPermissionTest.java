package com.leo.erp.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.service.UserAdminService;
import com.leo.erp.auth.web.dto.UserAccountResponse;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.PermissionMethodSecurityConfig;
import com.leo.erp.security.support.SecurityPrincipal;
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
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 用户管理端点授权抽查：读/写权限分离、通配放行、删除携带当前主体。 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        PermissionMethodSecurityConfig.class,
        V2UserAdminControllerPermissionTest.TestConfig.class
})
class V2UserAdminControllerPermissionTest {

    @Autowired
    private V2UserAdminController controller;

    @Autowired
    private UserAdminService userAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(userAdminService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void detail_withoutReadPermission_shouldReturn403() throws Exception {
        authenticate(new SecurityPrincipal(7L, "admin", "", true, 0L), PermissionCodes.USER_ACCOUNTS_WRITE);

        mockMvc.perform(get("/v2.0/users/1"))
                .andExpect(status().isForbidden());

        verify(userAdminService, never()).detail(anyLong());
    }

    @Test
    void detail_withReadPermission_shouldSucceed() throws Exception {
        when(userAdminService.detail(1L))
                .thenReturn(new UserAccountResponse(1L, "alice", "爱丽丝", null, UserStatus.NORMAL, null, null));
        authenticate(new SecurityPrincipal(7L, "admin", "", true, 0L), PermissionCodes.USER_ACCOUNTS_READ);

        mockMvc.perform(get("/v2.0/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginName").value("alice"));
    }

    @Test
    void create_withoutWritePermission_shouldReturn403() throws Exception {
        authenticate(new SecurityPrincipal(7L, "admin", "", true, 0L), PermissionCodes.USER_ACCOUNTS_READ);

        mockMvc.perform(post("/v2.0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"alice\",\"userName\":\"爱丽丝\",\"password\":\"Init@123\"}"))
                .andExpect(status().isForbidden());

        verify(userAdminService, never()).create(any());
    }

    @Test
    void create_withWildcardAuthority_shouldReturnCreated() throws Exception {
        when(userAdminService.create(any()))
                .thenReturn(new UserAccountResponse(100L, "alice", "爱丽丝", null, UserStatus.NORMAL, null, null));
        authenticate(new SecurityPrincipal(7L, "admin", "", true, 0L), PermissionCodes.WILDCARD);

        mockMvc.perform(post("/v2.0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"alice\",\"userName\":\"爱丽丝\",\"password\":\"Init@123\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v2.0/users/100"))
                .andExpect(jsonPath("$.loginName").value("alice"));
    }

    @Test
    void delete_withoutWritePermission_shouldReturn403() throws Exception {
        authenticate(new SecurityPrincipal(7L, "admin", "", true, 0L), PermissionCodes.USER_ACCOUNTS_READ);

        mockMvc.perform(delete("/v2.0/users/42"))
                .andExpect(status().isForbidden());

        verify(userAdminService, never()).delete(anyLong(), any());
    }

    @Test
    void delete_withWritePermission_shouldPassActingPrincipal() throws Exception {
        authenticate(new SecurityPrincipal(7L, "admin", "", true, 0L), PermissionCodes.USER_ACCOUNTS_WRITE);

        mockMvc.perform(delete("/v2.0/users/42"))
                .andExpect(status().isNoContent());

        verify(userAdminService).delete(42L, 7L);
    }

    private void authenticate(SecurityPrincipal principal, String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var authentication = new UsernamePasswordAuthenticationToken(principal, "n/a", granted);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Configuration
    static class TestConfig {

        @Bean
        UserAdminService userAdminService() {
            return mock(UserAdminService.class);
        }

        @Bean
        V2UserAdminController v2UserAdminController(UserAdminService userAdminService) {
            return new V2UserAdminController(userAdminService);
        }
    }
}
