package com.leo.erp.auth.web;

import com.leo.erp.auth.service.AuthRefreshResult;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LogoutRequest;
import com.leo.erp.auth.web.dto.RefreshTokenRequest;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.auth.web.support.AuthWebFlow;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.api.NoContent;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.web.PublicAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PublicAccess
@RestController
@Validated
@RequestMapping(ApiVersion.PREFIX + "/auth")
public class AuthController {

    private final AuthWebFlow authWebFlow;

    public AuthController(AuthWebFlow authWebFlow) {
        this.authWebFlow = authWebFlow;
    }

    @IdempotencyRequired
    @Operation(summary = "用户登录", description = "校验账号密码后签发访问令牌与刷新令牌（同时写入 HttpOnly Cookie 并返回 TokenResponse）；凭据错误返回 401。本端点为 @PublicAccess，免认证。")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse) {
        return authWebFlow.login(request, httpRequest, httpResponse);
    }

    @Operation(summary = "刷新访问令牌", description = "用刷新令牌（请求体或 Cookie）换取新的访问令牌；刷新令牌缺失、过期或已吊销返回 401。本端点为 @PublicAccess，免认证。")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody(required = false) RefreshTokenRequest request,
                                 HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        AuthRefreshResult result = authWebFlow.refresh(request, httpRequest, httpResponse);
        if (result.token() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, result.message());
        }
        return result.token();
    }

    @IdempotencyRequired
    @Operation(summary = "用户登出", description = "吊销当前会话的刷新令牌并清理 Cookie，成功返回 204 No Content；重复登出幂等。本端点为 @PublicAccess，免认证。")
    @PostMapping("/logout")
    @NoContent
    public ResponseEntity<Void> logout(@Valid @RequestBody(required = false) LogoutRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        authWebFlow.logout(request, httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }
}
