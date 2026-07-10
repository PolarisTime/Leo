package com.leo.erp.system.setup.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.setup.service.SetupTokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.Set;

@Component
@Order(InitialSetupTokenFilter.FILTER_ORDER)
public class InitialSetupTokenFilter extends OncePerRequestFilter {

    public static final String SETUP_TOKEN_HEADER = "X-Setup-Token";
    static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 4;

    private static final PathPattern SETUP_PATH = PathPatternParser.defaultInstance.parse("/setup/{*path}");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final SetupTokenVerifier tokenVerifier;
    private final ObjectMapper objectMapper;

    public InitialSetupTokenFilter(SetupTokenVerifier tokenVerifier, ObjectMapper objectMapper) {
        this.tokenVerifier = tokenVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !SETUP_PATH.matches(ServletRequestPathUtils.parse(request).pathWithinApplication());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        disableCaching(response);
        if (!SAFE_METHODS.contains(request.getMethod())
                && !tokenVerifier.matches(request.getHeader(SETUP_TOKEN_HEADER))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.failure(ErrorCode.FORBIDDEN, "初始化凭证无效")
            ));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void disableCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
    }

}
