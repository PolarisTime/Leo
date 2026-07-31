package com.leo.erp.common.config;

import com.leo.erp.common.api.ApiErrorResponseWriter;
import com.leo.erp.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class ReadOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    private final DatabaseReadOnlyProperties properties;
    private final ApiErrorResponseWriter errorResponseWriter;

    public ReadOnlyFilter(DatabaseReadOnlyProperties properties, ApiErrorResponseWriter errorResponseWriter) {
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (properties.readOnly() && WRITE_METHODS.contains(request.getMethod())) {
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN,
                    "数据库只读模式，禁止写操作"
            );
            return;
        }
        filterChain.doFilter(request, response);
    }
}
