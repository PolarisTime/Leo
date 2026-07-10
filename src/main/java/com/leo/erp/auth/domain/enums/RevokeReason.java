package com.leo.erp.auth.domain.enums;

public enum RevokeReason {
    MANUAL,
    CONCURRENT_LIMIT,
    EXPIRED,
    REUSE_DETECTED,
    PASSWORD_CHANGED
}
