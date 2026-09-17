package com.leo.erp.auth.web.dto;

import java.util.List;

/**
 * 登录/刷新返回的当前用户信息。
 *
 * <p>{@code permissions} 为该用户所有启用角色的权限码并集（含通配 {@code *} / {@code 资源:*}），
 * 供前端做菜单/按钮级可见性与可操作性控制；服务端仍以每次请求实时解析的权限为权威。</p>
 */
public record AuthUserResponse(
        Long id,
        String loginName,
        String userName,
        List<String> permissions
) {
}
