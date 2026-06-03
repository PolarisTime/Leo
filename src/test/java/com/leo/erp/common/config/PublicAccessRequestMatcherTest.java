package com.leo.erp.common.config;

import com.leo.erp.common.web.PublicAccess;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicAccessRequestMatcherTest {

    @Test
    void shouldMatchPublicAccessEndpoints() {
        RequestMappingHandlerMapping handlerMapping = handlerMappingWithPublicEndpoint();

        PublicAccessRequestMatcher matcher = new PublicAccessRequestMatcher(handlerMapping);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/test");
        request.setServletPath("/api/public/test");
        assertThat(matcher.matches(request)).isTrue();
    }

    @Test
    void shouldNotMatchNonPublicEndpoints() {
        RequestMappingHandlerMapping handlerMapping = handlerMappingWithPrivateEndpoint();

        PublicAccessRequestMatcher matcher = new PublicAccessRequestMatcher(handlerMapping);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private/test");
        assertThat(matcher.matches(request)).isFalse();
    }

    @Test
    void shouldReturnFalse_whenNoMappings() {
        RequestMappingHandlerMapping emptyMapping = mock(RequestMappingHandlerMapping.class);
        when(emptyMapping.getHandlerMethods()).thenReturn(Map.of());

        PublicAccessRequestMatcher matcher = new PublicAccessRequestMatcher(emptyMapping);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/test");
        assertThat(matcher.matches(request)).isFalse();
    }

    @Test
    void shouldNormalizeUriTemplates() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/api/public/{id}").build();
        HandlerMethod handlerMethod = new HandlerMethod(
                new PublicAccessTestController(),
                PublicAccessTestController.class.getMethod("publicWithPath"));
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(info, handlerMethod));

        PublicAccessRequestMatcher matcher = new PublicAccessRequestMatcher(handlerMapping);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/123");
        request.setServletPath("/api/public/123");
        assertThat(matcher.matches(request)).isTrue();
    }

    private RequestMappingHandlerMapping handlerMappingWithPublicEndpoint() {
        try {
            RequestMappingInfo info = RequestMappingInfo.paths("/api/public/test").build();
            HandlerMethod handlerMethod = new HandlerMethod(
                    new PublicAccessTestController(),
                    PublicAccessTestController.class.getMethod("publicEndpoint"));
            RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
            when(mapping.getHandlerMethods()).thenReturn(Map.of(info, handlerMethod));
            return mapping;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private RequestMappingHandlerMapping handlerMappingWithPrivateEndpoint() {
        try {
            RequestMappingInfo info = RequestMappingInfo.paths("/api/private/test").build();
            HandlerMethod handlerMethod = new HandlerMethod(
                    new PrivateTestController(),
                    PrivateTestController.class.getMethod("privateEndpoint"));
            RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
            when(mapping.getHandlerMethods()).thenReturn(Map.of(info, handlerMethod));
            return mapping;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @RestController
    static class PublicAccessTestController {
        @RequestMapping("/api/public/test")
        @PublicAccess
        public String publicEndpoint() { return "public"; }

        @RequestMapping("/api/public/{id}")
        @PublicAccess
        public String publicWithPath() { return "public"; }
    }

    @RestController
    static class PrivateTestController {
        @RequestMapping("/api/private/test")
        @com.leo.erp.security.permission.RequiresPermission(resource = "test", action = "read")
        public String privateEndpoint() { return "private"; }
    }
}
