package com.leo.erp.system.setup.web;

import com.leo.erp.common.api.ApiErrorResponseWriter;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.setup.service.SetupTokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    private static final Set<PathPattern> SETUP_PATHS = Set.of(
            PathPatternParser.defaultInstance.parse(ApiVersion.V2_PREFIX + "/setup/{*path}")
    );
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final SetupTokenVerifier tokenVerifier;
    private final ApiErrorResponseWriter errorResponseWriter;

    public InitialSetupTokenFilter(SetupTokenVerifier tokenVerifier, ApiErrorResponseWriter errorResponseWriter) {
        this.tokenVerifier = tokenVerifier;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = ServletRequestPathUtils.parse(request).pathWithinApplication();
        return SETUP_PATHS.stream().noneMatch(pattern -> pattern.matches(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        disableCaching(response);
        if (!SAFE_METHODS.contains(request.getMethod())
                && !tokenVerifier.matches(request.getHeader(SETUP_TOKEN_HEADER))) {
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN,
                    "初始化凭证无效"
            );
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
