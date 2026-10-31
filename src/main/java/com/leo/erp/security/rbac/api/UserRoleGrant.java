package com.leo.erp.security.rbac.api;

/**
 * 用户-角色授予端口，供其它模块在账号生命周期中登记角色。
 *
 * <p>例如初始化账号创建完成后需要授予超级管理员角色：这是 RBAC 模块的内部规则（角色编码、
 * 角色存在性、重复授予幂等），其它模块不应直接依赖 {@code security.rbac.service.UserRoleService}。
 * ArchUnit 跨模块 service 规则会拦住其它写法。</p>
 */
public interface UserRoleGrant {

    /** 为指定账号授予超级管理员角色；重复授予必须幂等，{@code userId} 为空时不做任何事。 */
    void grantSuperAdmin(Long userId);
}
