package com.leo.erp.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void shouldAllowOptionsRequestWithoutCheckingAuthentication() throws Exception {
        ForceTotpSetupFilter filter = new ForceTotpSetupFilter(new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/dashboard/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldAllowWhenAuthenticationPrincipalIsNotSecurityPrincipal() throws Exception {
        ForceTotpSetupFilter filter = new ForceTotpSetupFilter(new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("operator");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldAllowWhenForceSetupIsFalseOrTotpAlreadyEnabled() throws Exception {
        ForceTotpSetupFilter filter = new ForceTotpSetupFilter(new ObjectMapper().findAndRegisterModules());
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        authenticate(SecurityPrincipal.authenticated(1L, "tester", List.of(), false, false));

        filter.doFilter(new MockHttpServletRequest("GET", "/dashboard/summary"), firstResponse, new MockFilterChain());

        assertThat(firstResponse.getStatus()).isEqualTo(200);

        SecurityContextHolder.clearContext();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        authenticate(SecurityPrincipal.authenticated(1L, "tester", List.of(), true, true));

        filter.doFilter(new MockHttpServletRequest("GET", "/dashboard/summary"), secondResponse, new MockFilterChain());

        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldResolvePathWithoutContextPath() throws Exception {
        ForceTotpSetupFilter filter = new ForceTotpSetupFilter(new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/erp/account/security/2fa/setup");
        request.setContextPath("/erp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        authenticate(forcedPrincipal());

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldKeepOriginalPathWhenContextPathIsNull() throws Exception {
        ForceTotpSetupFilter filter = new ForceTotpSetupFilter(new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/account/security/2fa/setup") {
            @Override
            public String getContextPath() {
                return null;
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        authenticate(forcedPrincipal());

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldKeepOriginalPathWhenContextPathIsBlankOrNotPrefix() throws Exception {
        ForceTotpSetupFilter filter = new ForceTotpSetupFilter(new ObjectMapper().findAndRegisterModules());
        authenticate(forcedPrincipal());
        MockHttpServletRequest blankContextPathRequest = new MockHttpServletRequest("POST", "/erp/account/security/2fa/setup");
        blankContextPathRequest.setContextPath(" ");
        MockHttpServletResponse blankContextPathResponse = new MockHttpServletResponse();

        filter.doFilter(blankContextPathRequest, blankContextPathResponse, new MockFilterChain());

        assertThat(blankContextPathResponse.getStatus()).isEqualTo(403);

        MockHttpServletRequest mismatchedContextPathRequest = new MockHttpServletRequest("POST", "/account/security/2fa/setup");
        mismatchedContextPathRequest.setContextPath("/erp");
        MockHttpServletResponse mismatchedContextPathResponse = new MockHttpServletResponse();

        filter.doFilter(mismatchedContextPathRequest, mismatchedContextPathResponse, new MockFilterChain());

        assertThat(mismatchedContextPathResponse.getStatus()).isEqualTo(200);
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
