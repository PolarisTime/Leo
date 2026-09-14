package com.leo.erp.security.permission;

import com.leo.erp.security.support.SecurityPrincipal;
import java.util.Collection;

/**
 * 权限来源扩展点：根据当前认证主体解析其拥有的权限码集合。
 *
 * <p>这是为将来接入完整权限系统（角色/权限表、按部门/岗位授权）预留的插拔点。
 * 认证过滤器 {@code JwtAuthenticationFilter} 在构建 {@code Authentication} 时调用本接口，
 * 把返回的权限码转换为 {@code GrantedAuthority} 注入安全上下文，方法级授权随之生效。</p>
 *
 * <p>当前实现为 {@link GrantAllAuthorityProvider}：为任何登录用户返回全部权限码，
 * 与“单账号、登录即管理员”的既有信任模型保持一致。引入多角色后，应新增基于数据库
 * 角色-权限查询的 Provider，并通过 {@code @Primary}/{@code @ConditionalOnMissingBean}
 * 替换默认实现，无需改动控制器与鉴权逻辑。</p>
 */
public interface AuthorityProvider {

    /**
     * 返回指定登录用户拥有的权限码集合。
     *
     * @param principal 当前登录主体，可能为 {@link SecurityPrincipal#system()} 等系统主体
     * @return 权限码集合；返回空集合表示无任何权限
     */
    Collection<String> authoritiesFor(SecurityPrincipal principal);
}
