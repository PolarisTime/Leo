package com.leo.erp.system.setup.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.system.setup.config.InitialSetupProperties;
import com.leo.erp.system.setup.service.SetupTokenVerifier;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InitialSetupTokenFilterTest {

    private static final String VALID_TOKEN = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[32]);

    @Test
    void shouldAllowSetupWriteWithValidHeaderAndDisableCaching() throws Exception {
        InitialSetupTokenFilter filter = filter(VALID_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/setup/admin");
        request.setContextPath("/api");
        request.addHeader(InitialSetupTokenFilter.SETUP_TOKEN_HEADER, VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void shouldRejectSetupWriteWithoutConfiguredOrMatchingToken() throws Exception {
        assertRejected(filter(VALID_TOKEN), null);
        assertRejected(filter(VALID_TOKEN), "invalid");
        assertRejected(filter(null), VALID_TOKEN);
    }

    @Test
    void shouldRejectSetupWriteWhenPathContainsMatrixParameters() throws Exception {
        InitialSetupTokenFilter filter = filter(VALID_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/setup;x/admin");
        request.setContextPath("/api");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void shouldNotExposeProvidedOrConfiguredTokenInRejectionResponse() throws Exception {
        String providedToken = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[31]);
        InitialSetupTokenFilter filter = filter(VALID_TOKEN);
        MockHttpServletRequest request = setupWriteRequest(providedToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getContentAsString())
                .doesNotContain(VALID_TOKEN)
                .doesNotContain(providedToken);
    }

    @Test
    void shouldDisableCachingForPublicSetupStatusWithoutRequiringToken() throws Exception {
        InitialSetupTokenFilter filter = filter(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/setup/status");
        request.setContextPath("/api");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void shouldIgnoreRequestsOutsideSetupSurface() throws Exception {
        InitialSetupTokenFilter filter = filter(null);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setContextPath("/api");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("Cache-Control")).isNull();
    }

    private void assertRejected(InitialSetupTokenFilter filter, String providedToken) throws Exception {
        MockHttpServletRequest request = setupWriteRequest(providedToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentType()).startsWith("application/json");
        verify(chain, never()).doFilter(any(), any());
    }

    private MockHttpServletRequest setupWriteRequest(String providedToken) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/setup/initialize");
        request.setContextPath("/api");
        if (providedToken != null) {
            request.addHeader(InitialSetupTokenFilter.SETUP_TOKEN_HEADER, providedToken);
        }
        return request;
    }

    private InitialSetupTokenFilter filter(String configuredToken) {
        InitialSetupProperties properties = new InitialSetupProperties();
        properties.setBootstrapToken(configuredToken);
        return new InitialSetupTokenFilter(new SetupTokenVerifier(properties), new ObjectMapper());
    }
}
