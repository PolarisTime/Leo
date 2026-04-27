package com.leo.erp.security.jwt;

import java.util.List;

public record ApiKeyAuthenticationDetails(
        Object delegate,
        List<String> allowedResources,
        List<String> allowedActions
) {
}
