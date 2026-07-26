package com.leo.erp.auth.api;

public record SessionInvalidatedEvent(Long userId, String sessionTokenId, boolean isLogout) {
}
