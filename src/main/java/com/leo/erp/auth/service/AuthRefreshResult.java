package com.leo.erp.auth.service;

import com.leo.erp.auth.web.dto.TokenResponse;

public record AuthRefreshResult(
        String message,
        TokenResponse token
) {
}
