package com.leo.erp.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.domain.entity.ApiKey;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.ApiKeyRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.service.UserRoleBindingService;
import com.leo.erp.auth.support.ApiKeySupport;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final ApiKeyRepository apiKeyRepository;
    private final UserAccountRepository userAccountRepository;
    private final ObjectMapper objectMapper;
    private final UserRoleBindingService userRoleBindingService;
    private final ApiKeyUsageService apiKeyUsageService;

    public ApiKeyAuthenticationFilter(ApiKeyRepository apiKeyRepository,
                                      UserAccountRepository userAccountRepository,
                                      ObjectMapper objectMapper,
                                      UserRoleBindingService userRoleBindingService,
                                      ApiKeyUsageService apiKeyUsageService) {
        this.apiKeyRepository = apiKeyRepository;
        this.userAccountRepository = userAccountRepository;
        this.objectMapper = objectMapper;
        this.userRoleBindingService = userRoleBindingService;
        this.apiKeyUsageService = apiKeyUsageService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        ApiKey apiKey = apiKeyRepository.findByKeyHashAndDeletedFlagFalse(ApiKeySupport.hashKey(rawKey.trim()))
                .orElse(null);
        if (apiKey == null || !apiKey.isActive()) {
            sendFailure(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "API Key 无效或已失效");
            return;
        }

        String requestPath = resolveRequestPath(request);
        if (!isScopeAllowed(apiKey.getUsageScope(), requestPath, request.getMethod())) {
            sendFailure(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "当前 API Key 使用范围不允许访问该接口");
            return;
        }
        if (!isResourceAllowed(apiKey, request, requestPath)) {
            sendFailure(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "当前 API Key 未开通该资源接口权限");
            return;
        }
        if (!hasConfiguredActions(apiKey)) {
            sendFailure(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "当前 API Key 未配置动作权限");
            return;
        }

        UserAccount user = userAccountRepository.findByIdAndDeletedFlagFalse(apiKey.getUserId())
                .filter(candidate -> candidate.getStatus() == UserStatus.NORMAL)
                .orElse(null);
        if (user == null) {
            sendFailure(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "API Key 所属用户不存在或已禁用");
            return;
        }

        authenticate(request, user, apiKey);
        apiKeyUsageService.markUsed(apiKey.getId());
        filterChain.doFilter(request, response);
    }

    private boolean isScopeAllowed(String usageScope, String requestPath, String requestMethod) {
        return switch (usageScope) {
            case ApiKeySupport.SCOPE_ALL -> true;
            case ApiKeySupport.SCOPE_READ_ONLY -> READ_ONLY_METHODS.contains(requestMethod);
            case ApiKeySupport.SCOPE_BUSINESS -> isBusinessEndpoint(requestPath);
            default -> false;
        };
    }

    private boolean isResourceAllowed(ApiKey apiKey, HttpServletRequest request, String requestPath) {
        var allowedResources = ApiKeySupport.parseAllowedResources(apiKey.getAllowedResources());
        if (allowedResources.isEmpty()) {
            return true;
        }
        String resolvedCode = resolveResourceCode(request, requestPath);
        if ("/auth/ping".equals(requestPath)) {
            return true;
        }
        return resolvedCode != null && allowedResources.contains(resolvedCode);
    }

    private boolean hasConfiguredActions(ApiKey apiKey) {
        return !ApiKeySupport.parseAllowedActions(apiKey.getAllowedActions()).isEmpty();
    }

    private boolean isBusinessEndpoint(String path) {
        String resolvedCode = ResourcePermissionCatalog.resolveResourceByPath(path).orElse(null);
        if (resolvedCode == null) {
            return false;
        }
        return ResourcePermissionCatalog.isBusinessResource(resolvedCode);
    }

    private String resolveRequestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private String resolveResourceCode(HttpServletRequest request, String requestPath) {
        String resolvedCode = ResourcePermissionCatalog.resolveResourceByPath(requestPath).orElse(null);
        if (resolvedCode != null) {
            return resolvedCode;
        }
        if (requestPath != null && requestPath.startsWith("/attachments")) {
            String moduleKey = request.getParameter("moduleKey");
            if (moduleKey != null) {
                return ResourcePermissionCatalog.resolveResourceByMenuCode(moduleKey.trim())
                        .orElse(null);
            }
        }
        return null;
    }

    private void sendFailure(HttpServletResponse response, int httpStatus, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(errorCode, message));
    }

    private void authenticate(HttpServletRequest request, UserAccount user, ApiKey apiKey) {
        var boundRoles = userRoleBindingService.resolveRolesForUser(user.getId());
        SecurityPrincipal principal = SecurityPrincipal.authenticated(
                user.getId(),
                user.getLoginName(),
                userRoleBindingService.toGrantedAuthorities(boundRoles),
                Boolean.TRUE.equals(user.getTotpEnabled()),
                false
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        authentication.setDetails(new ApiKeyAuthenticationDetails(
                new WebAuthenticationDetailsSource().buildDetails(request),
                ApiKeySupport.parseAllowedResources(apiKey.getAllowedResources()),
                ApiKeySupport.parseAllowedActions(apiKey.getAllowedActions())
        ));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
