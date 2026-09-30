package com.leo.erp.security.rbac.repository;

import com.leo.erp.security.rbac.domain.entity.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SysUserRoleRepository extends JpaRepository<SysUserRole, Long> {

    List<SysUserRole> findByUserId(Long userId);

    long countByRoleId(Long roleId);

    List<SysUserRole> findByRoleIdIn(Collection<Long> roleIds);

    /**
     * 查询某角色下的全部用户 id（去重）。
     *
     * <p>用途：按范围失效权限缓存——角色权限/状态变化后，只需失效该角色下用户的缓存，
     * 而不是把所有用户的缓存一起清空（原实现换全局纪元，代价随用户数线性放大，
     * 且会让所有请求同时回源）。</p>
     */
    @Query("SELECT DISTINCT ur.userId FROM SysUserRole ur WHERE ur.roleId = :roleId AND ur.deletedFlag = false")
    List<Long> findUserIdsByRoleId(@Param("roleId") Long roleId);

    /** 批量查询多个角色下的用户 id（去重）。 */
    @Query("SELECT DISTINCT ur.userId FROM SysUserRole ur WHERE ur.roleId IN :roleIds AND ur.deletedFlag = false")
    List<Long> findUserIdsByRoleIdIn(@Param("roleIds") Collection<Long> roleIds);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SysUserRole ur WHERE ur.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
