package com.leo.erp.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForceTotpSetupFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldBlockProtectedRequestWhenForcedSetupIsPending() throws Exception {
        ForceTotpSetupFilter filter = new ForceTotpSetupFilter(new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticate(forcedPrincipal());

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("需先完成 2FA 绑定");
    }

    @Test
    void shouldAllowSecuritySetupEndpointsWhenForcedSetupIsPending() throws Exception {
        ForceTotpSetupFilter filter = new ForceTotpSetupFilter(new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/account/security/2fa/setup");
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticate(forcedPrincipal());

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private void authenticate(SecurityPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private SecurityPrincipal forcedPrincipal() {
        return SecurityPrincipal.authenticated(1L, "tester", List.of(), false, true);
    }
}
