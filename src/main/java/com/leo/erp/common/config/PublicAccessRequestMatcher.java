package com.leo.erp.common.config;

import com.leo.erp.common.web.PublicAccess;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PublicAccessRequestMatcher implements RequestMatcher {

    private static final Pattern URI_TEMPLATE_PATTERN = Pattern.compile("\\{[^/]+}");

    private final RequestMatcher delegate;

    public PublicAccessRequestMatcher(RequestMappingHandlerMapping handlerMapping) {
        List<RequestMatcher> matchers = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((mapping, handlerMethod) -> {
            boolean publicAccess = AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), PublicAccess.class)
                    || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PublicAccess.class);
            if (!publicAccess) {
                return;
            }
            matchers.addAll(buildMatchers(mapping));
        });
        this.delegate = matchers.isEmpty()
                ? request -> false
                : new OrRequestMatcher(matchers);
    }

    @Override
    public boolean matches(jakarta.servlet.http.HttpServletRequest request) {
        return delegate.matches(request);
    }

    private List<RequestMatcher> buildMatchers(RequestMappingInfo mapping) {
        List<RequestMatcher> matchers = new ArrayList<>();
        Set<String> patterns = resolvePatterns(mapping);
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        for (String pattern : patterns) {
            String normalizedPattern = normalizePattern(pattern);
            if (methods.isEmpty()) {
                matchers.add(new AntPathRequestMatcher(normalizedPattern));
                continue;
            }
            for (RequestMethod method : methods) {
                matchers.add(new AntPathRequestMatcher(normalizedPattern, method.name()));
            }
        }
        return matchers;
    }

    private Set<String> resolvePatterns(RequestMappingInfo mapping) {
        if (mapping.getPathPatternsCondition() != null) {
            return mapping.getPathPatternsCondition().getPatternValues();
        }
        PatternsRequestCondition patternsCondition = mapping.getPatternsCondition();
        return patternsCondition == null ? Set.of() : patternsCondition.getPatterns();
    }

    private String normalizePattern(String pattern) {
        return URI_TEMPLATE_PATTERN.matcher(pattern).replaceAll("*");
    }
}
