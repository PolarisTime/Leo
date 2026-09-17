package com.leo.erp.security.permission;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 服务层细粒度/字段级权限判断。
 *
 * <p>与 {@code @RequirePermission} 的方法级授权决策共用 {@link PermissionCodes#grants}
 * 的通配语义，避免出现"方法级放行、字段级拒绝"的不一致；当前主体权限来自安全上下文
 * （与认证过滤器、登录返回给前端的权限集合同源）。</p>
 */
@Component
public class PermissionChecker {

    /** 当前用户是否具备指定权限码。 */
    public boolean has(String code) {
        return hasCurrent(code);
    }

    /** 当前用户不具备指定权限码时抛出 403。 */
    public void require(String code) {
        if (!has(code)) {
            throw new AccessDeniedException("缺少权限: " + code);
        }
    }

    /**
     * 读取当前安全上下文的权限集合做判断；供 service 与字段级序列化器共用。
     * 无认证主体时返回 false（匿名不可见受保护字段）。
     */
    public static boolean hasCurrent(String code) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Set<String> granted = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            granted.add(authority.getAuthority());
        }
        return PermissionCodes.grants(granted, code);
    }
}
