package com.leo.erp.auth.service;

public record SessionInvalidatedEvent(Long userId, String sessionTokenId, boolean isLogout) {
}
