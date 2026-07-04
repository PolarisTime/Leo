package com.leo.erp.system.operationlog.support;

import com.leo.erp.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServletServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OperationLogResponseBodyAdviceTest {

    private final OperationLogResponseBodyAdvice advice = new OperationLogResponseBodyAdvice();

    @Test
    void shouldSupportAllReturnTypes() {
        var returnType = mock(MethodParameter.class);

        assertThat(advice.supports(returnType, null)).isTrue();
    }

    @Test
    void shouldSetAttribute_whenBodyIsApiResponse() {
        var servletRequest = mock(HttpServletRequest.class);
        var servletServerRequest = mock(ServletServerHttpRequest.class);
        when(servletServerRequest.getServletRequest()).thenReturn(servletRequest);

        var apiResponse = ApiResponse.success("data");

        Object result = advice.beforeBodyWrite(apiResponse, null, null, null, servletServerRequest, null);

        verify(servletRequest).setAttribute(OperationLogResponseBodyAdvice.RESPONSE_BODY_ATTRIBUTE, apiResponse);
        assertThat(result).isEqualTo(apiResponse);
    }

    @Test
    void shouldSetAttribute_whenBodyIsResponseEntityWithApiResponse() {
        var servletRequest = mock(HttpServletRequest.class);
        var servletServerRequest = mock(ServletServerHttpRequest.class);
        when(servletServerRequest.getServletRequest()).thenReturn(servletRequest);

        var apiResponse = ApiResponse.success("data");
        var responseEntity = ResponseEntity.ok(apiResponse);

        Object result = advice.beforeBodyWrite(responseEntity, null, null, null, servletServerRequest, null);

        verify(servletRequest).setAttribute(OperationLogResponseBodyAdvice.RESPONSE_BODY_ATTRIBUTE, apiResponse);
        assertThat(result).isEqualTo(responseEntity);
    }

    @Test
    void shouldPassThrough_whenResponseEntityBodyIsNotApiResponse() {
        var servletRequest = mock(HttpServletRequest.class);
        var servletServerRequest = mock(ServletServerHttpRequest.class);
        when(servletServerRequest.getServletRequest()).thenReturn(servletRequest);
        var responseEntity = ResponseEntity.ok("data");

        Object result = advice.beforeBodyWrite(responseEntity, null, null, null, servletServerRequest, null);

        verify(servletRequest, never()).setAttribute(anyString(), any());
        assertThat(result).isEqualTo(responseEntity);
    }

    @Test
    void shouldPassThrough_whenServletBodyIsNotApiResponse() {
        var servletRequest = mock(HttpServletRequest.class);
        var servletServerRequest = mock(ServletServerHttpRequest.class);
        when(servletServerRequest.getServletRequest()).thenReturn(servletRequest);

        Object result = advice.beforeBodyWrite("data", null, null, null, servletServerRequest, null);

        verify(servletRequest, never()).setAttribute(anyString(), any());
        assertThat(result).isEqualTo("data");
    }

    @Test
    void shouldPassThrough_whenRequestNotServletServerRequest() {
        var serverRequest = mock(org.springframework.http.server.ServerHttpRequest.class);
        var body = "some body";

        Object result = advice.beforeBodyWrite(body, null, null, null, serverRequest, null);

        assertThat(result).isEqualTo(body);
    }
}
