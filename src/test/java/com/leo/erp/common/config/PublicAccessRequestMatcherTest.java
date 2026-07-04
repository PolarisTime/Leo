package com.leo.erp.common.config;

import com.leo.erp.common.web.PublicAccess;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.ConsumesRequestCondition;
import org.springframework.web.servlet.mvc.condition.HeadersRequestCondition;
import org.springframework.web.servlet.mvc.condition.ParamsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.ProducesRequestCondition;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;

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

    @Test
    void shouldMatchClassLevelPublicAccessWithHttpMethod() throws Exception {
        RequestMappingInfo info = RequestMappingInfo
                .paths("/api/class-public/test")
                .methods(RequestMethod.POST)
                .build();
        HandlerMethod handlerMethod = new HandlerMethod(
                new ClassLevelPublicAccessController(),
                ClassLevelPublicAccessController.class.getMethod("classPublicEndpoint"));
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(info, handlerMethod));

        PublicAccessRequestMatcher matcher = new PublicAccessRequestMatcher(handlerMapping);

        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/class-public/test");
        post.setServletPath("/api/class-public/test");
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/class-public/test");
        get.setServletPath("/api/class-public/test");
        assertThat(matcher.matches(post)).isTrue();
        assertThat(matcher.matches(get)).isFalse();
    }

    @Test
    void shouldFallbackToLegacyPatternsCondition() throws Exception {
        RequestMappingInfo info = legacyRequestMappingInfo("/legacy/public/{id}");
        HandlerMethod handlerMethod = new HandlerMethod(
                new PublicAccessTestController(),
                PublicAccessTestController.class.getMethod("publicWithPath"));
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(info, handlerMethod));

        PublicAccessRequestMatcher matcher = new PublicAccessRequestMatcher(handlerMapping);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/legacy/public/100");
        request.setServletPath("/legacy/public/100");
        assertThat(matcher.matches(request)).isTrue();
    }

    @Test
    void shouldFallbackToLegacyPatternsConditionWhenPathPatternsAreEmpty() throws Exception {
        RequestMappingInfo info = mock(RequestMappingInfo.class);
        PathPatternsRequestCondition pathPatterns = mock(PathPatternsRequestCondition.class);
        when(pathPatterns.getPatternValues()).thenReturn(Set.of());
        when(info.getPathPatternsCondition()).thenReturn(pathPatterns);
        when(info.getPatternsCondition()).thenReturn(new PatternsRequestCondition("/legacy-empty/public/{id}"));
        when(info.getMethodsCondition()).thenReturn(new RequestMethodsRequestCondition());
        HandlerMethod handlerMethod = new HandlerMethod(
                new PublicAccessTestController(),
                PublicAccessTestController.class.getMethod("publicWithPath"));
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(info, handlerMethod));

        PublicAccessRequestMatcher matcher = new PublicAccessRequestMatcher(handlerMapping);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/legacy-empty/public/100");
        request.setServletPath("/legacy-empty/public/100");
        assertThat(matcher.matches(request)).isTrue();
    }

    @Test
    void shouldIgnoreLegacyMappingWithoutPatterns() throws Exception {
        RequestMappingInfo info = mock(RequestMappingInfo.class);
        when(info.getPathPatternsCondition()).thenReturn(null);
        when(info.getPatternsCondition()).thenReturn(null);
        when(info.getMethodsCondition()).thenReturn(new RequestMethodsRequestCondition());
        HandlerMethod handlerMethod = new HandlerMethod(
                new PublicAccessTestController(),
                PublicAccessTestController.class.getMethod("publicEndpoint"));
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(info, handlerMethod));

        PublicAccessRequestMatcher matcher = new PublicAccessRequestMatcher(handlerMapping);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/test");
        request.setServletPath("/api/public/test");
        assertThat(matcher.matches(request)).isFalse();
    }

    @Test
    void shouldUseMvcHandlerMappingWhenActuatorMappingAlsoExists() {
        RequestMappingHandlerMapping handlerMapping = handlerMappingWithPublicEndpoint();
        RequestMappingHandlerMapping actuatorMapping = mock(RequestMappingHandlerMapping.class);
        when(actuatorMapping.getHandlerMethods()).thenReturn(Map.of());

        new ApplicationContextRunner()
                .withBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class, () -> handlerMapping)
                .withBean("controllerEndpointHandlerMapping", RequestMappingHandlerMapping.class, () -> actuatorMapping)
                .withBean(PublicAccessRequestMatcher.class)
                .run(context -> assertThat(context).hasSingleBean(PublicAccessRequestMatcher.class));
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

    private RequestMappingInfo legacyRequestMappingInfo(String... patterns) {
        return new RequestMappingInfo(
                null,
                patterns.length == 0 ? null : new PatternsRequestCondition(patterns),
                new RequestMethodsRequestCondition(),
                new ParamsRequestCondition(),
                new HeadersRequestCondition(),
                new ConsumesRequestCondition(),
                new ProducesRequestCondition(),
                null);
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

    @RestController
    @PublicAccess
    static class ClassLevelPublicAccessController {
        @RequestMapping(value = "/api/class-public/test", method = RequestMethod.POST)
        public String classPublicEndpoint() { return "public"; }
    }
}
