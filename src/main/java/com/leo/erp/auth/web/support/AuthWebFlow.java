package com.leo.erp.auth.web.support;

import com.leo.erp.auth.service.AuthRefreshResult;
import com.leo.erp.auth.service.AuthSessionWebService;
import com.leo.erp.auth.service.LoginService;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LogoutRequest;
import com.leo.erp.auth.web.dto.RefreshTokenRequest;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.common.support.ClientIpResolver;
import com.leo.erp.security.jwt.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthWebFlow {

    private final AuthSessionWebService authSessionWebService;
    private final AuthTokenCookieSupport authTokenCookieSupport;
    private final ClientIpResolver clientIpResolver;
    private final JwtTokenService jwtTokenService;

    public AuthWebFlow(AuthSessionWebService authSessionWebService,
                       AuthTokenCookieSupport authTokenCookieSupport,
                       ClientIpResolver clientIpResolver,
                       JwtTokenService jwtTokenService) {
        this.authSessionWebService = authSessionWebService;
        this.authTokenCookieSupport = authTokenCookieSupport;
        this.clientIpResolver = clientIpResolver;
        this.jwtTokenService = jwtTokenService;
    }

    public TokenResponse login(LoginRequest request,
                               HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse) {
        TokenResponse result = authSessionWebService.login(request, resolveAuthContext(httpRequest));
        String refreshToken = result.refreshTokenForCookie();
        if (refreshToken == null || refreshToken.isBlank()) {
            return result;
        }
        authTokenCookieSupport.writeRefreshTokenCookie(httpResponse, refreshToken, refreshTokenMaxAge());
        return result.withoutRefreshToken();
    }

    public AuthRefreshResult refresh(RefreshTokenRequest request,
                                     HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        String refreshToken = authTokenCookieSupport.resolveRefreshToken(
                httpRequest,
                request == null ? null : request.refreshToken()
        );
        AuthRefreshResult result = authSessionWebService.refresh(
                refreshToken,
                clientIpResolver.resolveClientIpOrUnknown(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
        if (result.token() == null) {
            authTokenCookieSupport.clearRefreshTokenCookie(httpResponse);
            return result;
        }
        authTokenCookieSupport.writeRefreshTokenCookie(
                httpResponse,
                result.token().refreshToken(),
                refreshTokenMaxAge()
        );
        return new AuthRefreshResult(result.message(), result.token().withoutRefreshToken());
    }

    public void logout(LogoutRequest request,
                       HttpServletRequest httpRequest,
                       HttpServletResponse httpResponse) {
        authSessionWebService.logout(
                authTokenCookieSupport.resolveRefreshToken(
                        httpRequest,
                        request == null ? null : request.refreshToken()
                ),
                resolveAuthContext(httpRequest)
        );
        authTokenCookieSupport.clearRefreshTokenCookie(httpResponse);
    }

    private LoginService.AuthRequestContext resolveAuthContext(HttpServletRequest request) {
        return new LoginService.AuthRequestContext(
                clientIpResolver.resolveClientIpOrUnknown(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod()
        );
    }

    private Duration refreshTokenMaxAge() {
        return Duration.ofMillis(jwtTokenService.getRefreshExpirationMs());
    }
}
