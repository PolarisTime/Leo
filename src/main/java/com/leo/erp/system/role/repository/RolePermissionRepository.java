package com.leo.erp.system.role.repository;

import com.leo.erp.system.role.domain.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleIdAndDeletedFlagFalse(Long roleId);

    List<RolePermission> findByRoleIdInAndDeletedFlagFalse(List<Long> roleIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RolePermission rp where rp.roleId = :roleId and rp.deletedFlag = false")
    int deleteActiveByRoleId(@Param("roleId") Long roleId);
}
