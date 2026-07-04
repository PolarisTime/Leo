package com.leo.erp.common.web;

import com.leo.erp.common.api.PageQuery;
import jakarta.servlet.http.HttpServletRequest;
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

    private final PageQuerySettings pageQuerySettings = mock(PageQuerySettings.class);
    private final PageQueryArgumentResolver resolver = new PageQueryArgumentResolver(pageQuerySettings);

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
        when(pageQuerySettings.getDefaultListPageSize()).thenReturn(20);

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

    @Test
    void shouldResolvePageQueryWithBindingAnnotation() throws Exception {
        Method method = TestController.class.getMethod("annotatedMethod", PageQuery.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("p", " 3 ");
        request.setParameter("s", " ");
        request.setParameter("orderBy", "customerCode");
        request.setParameter("order", "asc");
        NativeWebRequest webRequest = new ServletWebRequest(request);
        when(pageQuerySettings.getDefaultListPageSize()).thenReturn(30);

        PageQuery result = (PageQuery) resolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(result.page()).isEqualTo(3);
        assertThat(result.size()).isEqualTo(30);
        assertThat(result.sortBy()).isEqualTo("customerCode");
        assertThat(result.direction()).isEqualTo("asc");
    }

    @Test
    void shouldThrowWhenNativeRequestIsNotHttpServletRequest() throws Exception {
        Method method = TestController.class.getMethod("testMethod", PageQuery.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, webRequest, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Current request is not an HTTP request");
    }

    static class TestController {
        public void testMethod(PageQuery pageQuery) {}
        public void otherMethod(String param) {}
        public void annotatedMethod(
                @BindPageQuery(
                        pageParam = "p",
                        sizeParam = "s",
                        sortByParam = "orderBy",
                        directionParam = "order",
                        sortFieldKey = "customer"
                ) PageQuery pageQuery) {}
    }
}
