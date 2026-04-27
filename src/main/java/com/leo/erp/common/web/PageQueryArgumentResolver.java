package com.leo.erp.common.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageSortFieldCatalog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class PageQueryArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(PageQuery.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  @Nullable ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  @Nullable WebDataBinderFactory binderFactory) {
        BindPageQuery binding = parameter.getParameterAnnotation(BindPageQuery.class);
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("Current request is not an HTTP request");
        }

        String pageParam = binding == null ? "page" : binding.pageParam();
        String sizeParam = binding == null ? "size" : binding.sizeParam();
        String sortByParam = binding == null ? "sortBy" : binding.sortByParam();
        String directionParam = binding == null ? "direction" : binding.directionParam();
        String sortFieldKey = binding == null ? "" : binding.sortFieldKey();

        return PageQuery.of(
                toInteger(request.getParameter(pageParam)),
                toInteger(request.getParameter(sizeParam)),
                request.getParameter(sortByParam),
                request.getParameter(directionParam),
                sortFieldKey.isBlank() ? null : PageSortFieldCatalog.fields(sortFieldKey)
        );
    }

    private Integer toInteger(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Integer.valueOf(raw.trim());
    }
}
