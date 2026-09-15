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

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SysUserRole ur WHERE ur.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
