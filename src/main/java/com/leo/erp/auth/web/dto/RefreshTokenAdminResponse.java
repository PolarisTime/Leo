package com.leo.erp.auth.web.dto;

import java.time.LocalDateTime;

public record RefreshTokenAdminResponse(
        Long id,
        Long userId,
        String loginName,
        String userName,
        String tokenId,
        String loginIp,
        String deviceInfo,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt,
        String status,
        LocalDateTime lastActiveAt,
        boolean online
) {
}
