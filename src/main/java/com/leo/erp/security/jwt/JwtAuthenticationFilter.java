package com.leo.erp.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.support.ApiKeySupport;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.RateLimitContext;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.support.SecurityPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Date;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final AuthenticatedUserCacheService authenticatedUserCacheService;
    private final AccessTokenBlacklistService blacklistService;
    private final SessionActivityService sessionActivityService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   AuthenticatedUserCacheService authenticatedUserCacheService,
                                   AccessTokenBlacklistService blacklistService,
                                   SessionActivityService sessionActivityService,
                                   ObjectMapper objectMapper) {
        this.jwtTokenService = jwtTokenService;
        this.authenticatedUserCacheService = authenticatedUserCacheService;
        this.blacklistService = blacklistService;
        this.sessionActivityService = sessionActivityService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7).trim();
        if (token.startsWith(ApiKeySupport.RAW_KEY_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Claims claims = jwtTokenService.parseAccessToken(token);
            Long userId = extractUserId(claims);
            String sessionId = extractSessionId(claims);
            if (userId != null) {
                // 检查 access token 是否在黑名单中（签发时间早于黑名单时间则视为无效）
                if (isTokenBlacklisted(claims, userId, sessionId)) {
                    sendUnauthorized(request, response, "会话已失效，请重新登录");
                    return;
                }

                authenticatedUserCacheService.getActivePrincipal(userId)
                        .ifPresent(principal -> {
                            authenticate(request, principal);
                            sessionActivityService.touchSession(sessionId);
                        });
            }
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn(
                    "JWT authentication failed: method={}, path={}, reason={}, message={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            sendUnauthorized(request, response, "登录状态已失效，请重新登录");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Long extractUserId(Claims claims) {
        Object uid = claims.get("uid");
        return uid == null ? null : Long.parseLong(String.valueOf(uid));
    }

    private String extractSessionId(Claims claims) {
        Object sid = claims.get("sid");
        return sid == null ? null : String.valueOf(sid);
    }

    private boolean isTokenBlacklisted(Claims claims, Long userId, String sessionId) {
        if (sessionId != null && blacklistService.isSessionBlacklisted(sessionId)) {
            return true;
        }
        if (!blacklistService.isBlacklisted(userId)) {
            return false;
        }
        // 如果 token 签发时间早于黑名单时间，则视为无效
        Date issuedAt = claims.getIssuedAt();
        long blacklistTime = blacklistService.getBlacklistTime(userId);
        return issuedAt != null && issuedAt.getTime() < blacklistTime;
    }

    private void sendUnauthorized(HttpServletRequest request,
                                  HttpServletResponse response,
                                  String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(ErrorCode.UNAUTHORIZED, message, RateLimitContext.current(request))
        );
    }

    private void authenticate(HttpServletRequest request, SecurityPrincipal principal) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
