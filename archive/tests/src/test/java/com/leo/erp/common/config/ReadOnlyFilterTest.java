package com.leo.erp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.RateLimitContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReadOnlyFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readOnlyMode_blocksWriteRequest() throws Exception {
        DatabaseReadOnlyProperties properties = new DatabaseReadOnlyProperties(true);
        ReadOnlyFilter filter = new ReadOnlyFilter(properties, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("只读");
        verify(chain, never()).doFilter(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void readOnlyMode_blocksAllWriteMethods(String method) throws Exception {
        DatabaseReadOnlyProperties properties = new DatabaseReadOnlyProperties(true);
        ReadOnlyFilter filter = new ReadOnlyFilter(properties, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void readOnlyMode_allowsGetRequest() throws Exception {
        DatabaseReadOnlyProperties properties = new DatabaseReadOnlyProperties(true);
        ReadOnlyFilter filter = new ReadOnlyFilter(properties, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void readWriteMode_allowsWriteRequest() throws Exception {
        DatabaseReadOnlyProperties properties = new DatabaseReadOnlyProperties(false);
        ReadOnlyFilter filter = new ReadOnlyFilter(properties, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
