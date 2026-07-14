package com.leo.erp.system.operationlog.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.ContentCachingRequestWrapper;

import static org.mockito.Mockito.*;

class OperationLogRequestFilterTest {

    @Test
    void shouldWrapRequest_whenNotAlreadyWrapped() throws Exception {
        var filter = new OperationLogRequestFilter();
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(ContentCachingRequestWrapper.class), eq(response));
    }

    @Test
    void shouldPassThrough_whenAlreadyWrapped() throws Exception {
        var filter = new OperationLogRequestFilter();
        var request = mock(ContentCachingRequestWrapper.class);
        var response = mock(HttpServletResponse.class);
        var filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
