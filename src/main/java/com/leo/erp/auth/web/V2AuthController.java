package com.leo.erp.auth.web;

import com.leo.erp.auth.service.AuthRefreshResult;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LogoutRequest;
import com.leo.erp.auth.web.dto.RefreshTokenRequest;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.auth.web.support.AuthWebFlow;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.web.PublicAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PublicAccess
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/auth")
public class V2AuthController {

    private final AuthWebFlow authWebFlow;

    public V2AuthController(AuthWebFlow authWebFlow) {
        this.authWebFlow = authWebFlow;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse) {
        return authWebFlow.login(request, httpRequest, httpResponse);
    }

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

    @PostMapping("/logout")
    @V2NoContent
    public ResponseEntity<Void> logout(@Valid @RequestBody(required = false) LogoutRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        authWebFlow.logout(request, httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }
}
