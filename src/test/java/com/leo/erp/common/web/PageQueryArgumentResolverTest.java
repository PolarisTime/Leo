package com.leo.erp.common.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.system.norule.service.SystemSwitchService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PageQueryArgumentResolverTest {

    private final SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
    private final PageQueryArgumentResolver resolver = new PageQueryArgumentResolver(systemSwitchService);

    @Test
    void shouldSupportPageQueryParameter() throws NoSuchMethodException {
        Method method = TestController.class.getMethod("testMethod", PageQuery.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    void shouldNotSupportNonPageQueryParameter() throws NoSuchMethodException {
        Method method = TestController.class.getMethod("otherMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    void shouldResolvePageQueryWithDefaultValues() throws Exception {
        when(systemSwitchService.getDefaultListPageSize()).thenReturn(20);

        Method method = TestController.class.getMethod("testMethod", PageQuery.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        MockHttpServletRequest request = new MockHttpServletRequest();
        NativeWebRequest webRequest = new ServletWebRequest(request);

        PageQuery result = (PageQuery) resolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(result).isNotNull();
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
    }

    @Test
    void shouldResolvePageQueryWithCustomValues() throws Exception {
        Method method = TestController.class.getMethod("testMethod", PageQuery.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("page", "2");
        request.setParameter("size", "50");
        NativeWebRequest webRequest = new ServletWebRequest(request);

        PageQuery result = (PageQuery) resolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(50);
    }

    static class TestController {
        public void testMethod(PageQuery pageQuery) {}
        public void otherMethod(String param) {}
    }
}
