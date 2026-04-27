package com.leo.erp.auth.web.dto;

public record RefreshTokenSessionSummaryResponse(
        long onlineUsers,
        long onlineSessions,
        long activeSessions
) {
}
