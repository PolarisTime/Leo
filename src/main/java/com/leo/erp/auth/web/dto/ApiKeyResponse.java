package com.leo.erp.auth.web.dto;

import java.util.List;
import java.time.LocalDateTime;

public record ApiKeyResponse(
        Long id,
        Long userId,
        String loginName,
        String userName,
        String keyName,
        String usageScope,
        List<String> allowedResources,
        List<String> allowedActions,
        String keyPrefix,
        String rawKey,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime lastUsedAt,
        String status
) {
}
