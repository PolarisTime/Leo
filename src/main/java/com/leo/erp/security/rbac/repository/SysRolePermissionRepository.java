package com.leo.erp.security.rbac.repository;

import com.leo.erp.security.rbac.domain.entity.SysRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SysRolePermissionRepository extends JpaRepository<SysRolePermission, Long> {

    List<SysRolePermission> findByRoleId(Long roleId);

    long countByRoleId(Long roleId);

    List<SysRolePermission> findByRoleIdIn(Collection<Long> roleIds);

    /**
     * 整角色删除。
     *
     * <p>注意：这是「先删后插」的一部分，**不要在并发替换权限时使用**——
     * 并发路径请用 {@link #deleteByRoleIdAndPermissionCodeIn} 做增量删除，
     * 否则「删除 + 重建」会撞唯一索引并产生大量 409（实测 74.9% 的请求被拒）。</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SysRolePermission rp WHERE rp.roleId = :roleId")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 只删除「本次确实被移除」的权限码。
     *
     * <p>用于增量替换：如果目标集合没有变化，就一条语句都不发——
     * 这是并发写同一目标集合时冲突率从 74.9% 降到接近 0 的关键。</p>
     */
    @Modifying
    @Query("DELETE FROM SysRolePermission rp WHERE rp.roleId = :roleId AND rp.permissionCode IN :codes")
    int deleteByRoleIdAndPermissionCodeIn(@Param("roleId") Long roleId,
                                          @Param("codes") Collection<String> codes);

    /**
     * 聚合某个登录用户所有启用角色（去重）的权限码，供角色权限提供者使用。
     *
     * <p>使用原生 SQL 直接关联三张表，避免为此引入实体关联。</p>
     */
    @Query(value = """
            SELECT DISTINCT rp.permission_code
              FROM sys_role_permission rp
              JOIN sys_role r ON r.id = rp.role_id
              JOIN sys_user_role ur ON ur.role_id = r.id
             WHERE ur.user_id = :userId
               AND ur.deleted_flag = false
               AND r.deleted_flag = false
               AND r.status = :activeStatus
               AND rp.deleted_flag = false
            """, nativeQuery = true)
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId,
                                             @Param("activeStatus") String activeStatus);
}
