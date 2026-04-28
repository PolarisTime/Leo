package com.leo.erp.auth.repository;

import com.leo.erp.auth.domain.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserIdAndDeletedFlagFalse(Long userId);

    void deleteByUserIdAndDeletedFlagFalse(Long userId);

    List<UserRole> findByRoleIdInAndDeletedFlagFalse(List<Long> roleIds);

    long countByRoleIdAndDeletedFlagFalse(Long roleId);

    @Query("""
            select count(userRole)
            from UserRole userRole
            join UserAccount userAccount on userAccount.id = userRole.userId
            where userRole.roleId = :roleId
              and userRole.deletedFlag = false
              and userAccount.deletedFlag = false
            """)
    long countActiveUsersByRoleId(@Param("roleId") Long roleId);
}
