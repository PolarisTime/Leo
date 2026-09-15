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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SysRolePermission rp WHERE rp.roleId = :roleId")
    int deleteByRoleId(@Param("roleId") Long roleId);

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
