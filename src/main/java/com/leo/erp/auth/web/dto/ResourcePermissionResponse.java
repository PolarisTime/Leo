package com.leo.erp.auth.web.dto;

import java.util.Set;

public record ResourcePermissionResponse(
        String resource,
        Set<String> actions
) {
}
