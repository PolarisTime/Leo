package com.leo.erp.security.jwt;

import com.leo.erp.common.api.ApiErrorResponseWriter;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.config.AuthFallbackProperties;
import com.leo.erp.security.permission.AuthorityProvider;
import com.leo.erp.security.support.SecurityPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final AuthenticatedUserCacheService authenticatedUserCacheService;
    private final AccessTokenBlacklistService blacklistService;
    private final SessionActivityService sessionActivityService;
    private final ApiErrorResponseWriter errorResponseWriter;
    private final AuthorityProvider authorityProvider;
    private final AuthFallbackProperties authFallbackProperties;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   AuthenticatedUserCacheService authenticatedUserCacheService,
                                   AccessTokenBlacklistService blacklistService,
                                   SessionActivityService sessionActivityService,
                                   ApiErrorResponseWriter errorResponseWriter,
                                   AuthorityProvider authorityProvider,
                                   AuthFallbackProperties authFallbackProperties) {
        this.jwtTokenService = jwtTokenService;
        this.authenticatedUserCacheService = authenticatedUserCacheService;
        this.blacklistService = blacklistService;
        this.sessionActivityService = sessionActivityService;
        this.errorResponseWriter = errorResponseWriter;
        this.authorityProvider = authorityProvider;
        this.authFallbackProperties = authFallbackProperties == null ? new AuthFallbackProperties() : authFallbackProperties;
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
        try {
            Claims claims = jwtTokenService.parseAccessToken(token);
            Long userId = extractUserId(claims);
            String sessionId = extractSessionId(claims);
            if (userId != null) {
                // 吊销校验：Redis 读取失败时不能当成「未吊销」（fail-open），
                // 默认 fail-closed 直接 503；只有在显式开启有界降级窗口时，
                // 才允许本窗口内跳过吊销校验，由主体快照的 TTL 兜底（详见 AuthFallbackProperties）。
                boolean revocationUnverifiable = false;
                try {
                    if (isTokenBlacklisted(claims, userId, sessionId)) {
                        sendUnauthorized(request, response, "会话已失效，请重新登录");
                        return;
                    }
                } catch (DataAccessException ex) {
                    if (!authFallbackProperties.isEnabled()) {
                        log.error("Redis 不可用且未启用降级窗口，吊销校验无法完成: method={}, path={}, reason={}",
                                request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName());
                        sendServiceUnavailable(request, response);
                        return;
                    }
                    revocationUnverifiable = true;
                    log.warn("Redis 不可用，降级窗口内跳过吊销校验: method={}, path={}, userId={}, reason={}",
                            request.getMethod(), request.getRequestURI(), userId, ex.getClass().getSimpleName());
                }

                long tokenCredentialVersion = extractCredentialVersion(claims);
                authenticatedUserCacheService.getActivePrincipal(userId, tokenCredentialVersion)
                        .ifPresent(principal -> {
                            authenticate(request, principal);
                            touchSessionQuietly(sessionId);
                        });
                if (revocationUnverifiable) {
                    log.warn("本次请求在降级模式下放行（吊销校验不可用）: method={}, path={}, userId={}",
                            request.getMethod(), request.getRequestURI(), userId);
                }
            }
        } catch (AuthenticationInfrastructureException ex) {
            // 认证依赖不可用：必须 503，而不是让 RedisConnectionFailureException 穿透成 500。
            // 实测（2026-09-30 报告第 4 项）：Redis 断开时所有已登录请求都会走到这里。
            log.error("认证依赖不可用，返回 503: method={}, path={}, reason={}",
                    request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName());
            sendServiceUnavailable(request, response);
            return;
        } catch (DataAccessException ex) {
            log.error("认证过程中依赖访问失败，返回 503: method={}, path={}, reason={}",
                    request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName());
            sendServiceUnavailable(request, response);
            return;
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

    private long extractCredentialVersion(Claims claims) {
        Object credentialVersion = claims.get("cv");
        return credentialVersion == null ? 0L : Long.parseLong(String.valueOf(credentialVersion));
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
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED,
                message
        );
    }

    /**
     * 在线活跃时间戳是尽力而为的旁路写入：失败不应把已经认证成功的请求打成 5xx。
     */
    private void touchSessionQuietly(String sessionId) {
        try {
            sessionActivityService.touchSession(sessionId);
        } catch (RuntimeException ex) {
            log.warn("会话活跃时间戳写入失败（忽略，不影响本次认证）: sessionId={}, reason={}",
                    sessionId, ex.getClass().getSimpleName());
        }
    }

    /**
     * 依赖不可用时的响应：503 + {@code Retry-After}。
     *
     * <p>与 401 的区别很重要——401 会让客户端清空登录态并跳登录页，
     * 而依赖抖动时登录态本身是好的，只需要稍后重试。</p>
     */
    private void sendServiceUnavailable(HttpServletRequest request,
                                       HttpServletResponse response) throws IOException {
        response.setHeader("Retry-After", "5");
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SERVICE_UNAVAILABLE,
                "认证服务暂不可用（依赖的 Redis 不可用），请稍后重试"
        );
    }

    private void authenticate(HttpServletRequest request, SecurityPrincipal principal) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                resolveAuthorities(principal)
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Collection<GrantedAuthority> resolveAuthorities(SecurityPrincipal principal) {
        Collection<String> permissionCodes = authorityProvider.authoritiesFor(principal);
        List<GrantedAuthority> authorities = new ArrayList<>(permissionCodes.size());
        for (String permissionCode : permissionCodes) {
            authorities.add(new SimpleGrantedAuthority(permissionCode));
        }
        return authorities;
    }
}
