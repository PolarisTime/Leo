package com.leo.erp.auth.support;

import com.leo.erp.security.permission.ResourcePermissionCatalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ApiKeySupport {

    public static final String SCOPE_ALL = "全部接口";
    public static final String SCOPE_READ_ONLY = "只读接口";
    public static final String SCOPE_BUSINESS = "业务接口";
    public static final Set<String> ALLOWED_USAGE_SCOPE = Set.of(SCOPE_ALL, SCOPE_READ_ONLY, SCOPE_BUSINESS);
    private static final String LIST_SEPARATOR = ",";

    private ApiKeySupport() {
    }

    public static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256不可用", ex);
        }
    }

    public static List<String> parseAllowedResources(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        return List.of(rawValue.split(LIST_SEPARATOR)).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    public static String joinAllowedResources(List<String> allowedResources) {
        if (allowedResources == null || allowedResources.isEmpty()) {
            return "";
        }
        return allowedResources.stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.joining(LIST_SEPARATOR));
    }

    public static List<String> parseAllowedActions(String rawValue) {
        return parseAllowedResources(rawValue);
    }

    public static String joinAllowedActions(List<String> allowedActions) {
        return joinAllowedResources(allowedActions);
    }

    public static List<String> normalizeAllowedResources(List<String> allowedResources) {
        if (allowedResources == null || allowedResources.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String allowedResource : allowedResources) {
            if (allowedResource == null || allowedResource.isBlank()) {
                continue;
            }
            String code = ResourcePermissionCatalog.normalizeResource(allowedResource);
            if (!ResourcePermissionCatalog.isKnownResource(code)) {
                throw new IllegalArgumentException("API Key 允许访问资源不合法");
            }
            normalized.add(code);
        }
        return List.copyOf(normalized);
    }

    public static List<String> normalizeAllowedActions(List<String> allowedActions) {
        if (allowedActions == null || allowedActions.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String allowedAction : allowedActions) {
            if (allowedAction == null || allowedAction.isBlank()) {
                continue;
            }
            String code = ResourcePermissionCatalog.normalizeAction(allowedAction);
            if (!ResourcePermissionCatalog.isKnownAction(code)) {
                throw new IllegalArgumentException("API Key 允许动作不合法");
            }
            normalized.add(code);
        }
        return List.copyOf(normalized);
    }
}
