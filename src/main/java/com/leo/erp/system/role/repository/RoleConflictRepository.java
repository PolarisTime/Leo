package com.leo.erp.system.role.repository;

import com.leo.erp.system.role.domain.entity.RoleConflict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RoleConflictRepository extends JpaRepository<RoleConflict, Long> {

    @Query("""
            select rc from RoleConflict rc
            where rc.deletedFlag = false
              and rc.roleId in :roleIds
            """)
    List<RoleConflict> findConflictsByRoleIds(@Param("roleIds") Collection<Long> roleIds);
}
