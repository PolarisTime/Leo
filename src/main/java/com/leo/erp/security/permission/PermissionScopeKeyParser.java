package com.leo.erp.security.permission;

final class PermissionScopeKeyParser {

    private PermissionScopeKeyParser() {
    }

    static String key(String resource, String action) {
        if (resource == null || resource.isBlank() || action == null || action.isBlank()) {
            return "";
        }
        return resource + ":" + action;
    }

    static String normalize(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        int separator = key.indexOf(':');
        if (separator <= 0 || separator >= key.length() - 1) {
            return "";
        }
        String resource = ResourcePermissionCatalog.normalizeResource(key.substring(0, separator));
        String action = ResourcePermissionCatalog.normalizeAction(key.substring(separator + 1));
        return key(resource, action);
    }

    static String parseResource(String key) {
        String normalizedKey = normalize(key);
        int separator = normalizedKey.indexOf(':');
        return separator <= 0 ? "" : normalizedKey.substring(0, separator);
    }
}
