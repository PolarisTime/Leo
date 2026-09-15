package com.leo.erp.security.rbac.service;

import com.leo.erp.security.permission.PermissionCodes;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 权限码解析与合法性校验：以 {@link PermissionCodes#all()} 为单一来源。
 *
 * <p>合法权限码包含三类：目录内的具体权限码、全局通配 {@link PermissionCodes#WILDCARD}、
 * 以及资源级通配 {@code 资源:*}（资源必须存在于目录中）。</p>
 */
public final class PermissionCodeRules {

    private static final Pattern RESOURCE_WILDCARD = Pattern.compile("^[a-z][a-z0-9-]*:\\*$");

    private static final Set<String> KNOWN_RESOURCES = buildKnownResources();

    private PermissionCodeRules() {
    }

    /** 判断权限码是否合法（目录项、全局通配或已知资源的资源级通配）。 */
    public static boolean isKnown(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        if (PermissionCodes.WILDCARD.equals(code)) {
            return true;
        }
        if (PermissionCodes.all().contains(code)) {
            return true;
        }
        return isResourceWildcard(code);
    }

    /** 判断是否为 {@code 资源:*} 资源级通配，且资源存在于目录中。 */
    public static boolean isResourceWildcard(String code) {
        if (code == null || !RESOURCE_WILDCARD.matcher(code).matches()) {
            return false;
        }
        String resource = code.substring(0, code.indexOf(':'));
        return KNOWN_RESOURCES.contains(resource);
    }

    /** 解析权限码为资源/动作/字段三段；通配 {@code *} 解析为资源与动作均为 {@code *}。 */
    public static PermissionParts parse(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("权限码不能为空");
        }
        if (PermissionCodes.WILDCARD.equals(code)) {
            return new PermissionParts(PermissionCodes.WILDCARD, PermissionCodes.WILDCARD, null);
        }
        String[] segments = code.split(":", -1);
        String resource = segments[0];
        String action = segments.length > 1 && !segments[1].isEmpty() ? segments[1] : PermissionCodes.WILDCARD;
        String field = segments.length > 2 && !segments[2].isEmpty() ? segments[2] : null;
        return new PermissionParts(resource, action, field);
    }

    private static Set<String> buildKnownResources() {
        Set<String> resources = new LinkedHashSet<>();
        for (String code : PermissionCodes.all()) {
            if (PermissionCodes.WILDCARD.equals(code)) {
                continue;
            }
            int separator = code.indexOf(':');
            if (separator > 0) {
                resources.add(code.substring(0, separator));
            }
        }
        return Set.copyOf(resources);
    }

    /** 权限码的三段视图。 */
    public record PermissionParts(String resource, String action, String field) {
    }
}
