package com.leo.erp.auth.domain.enums;

public enum ApiKeyStatus {
    ACTIVE("有效"),
    DISABLED("已禁用");

    private final String displayName;

    ApiKeyStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ApiKeyStatus fromDisplayName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        for (ApiKeyStatus status : values()) {
            if (status.displayName.equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ApiKeyStatus display name: " + value);
    }
}
