package com.leo.erp.auth.repository;

import com.leo.erp.auth.domain.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserIdAndDeletedFlagFalse(Long userId);

    void deleteByUserIdAndDeletedFlagFalse(Long userId);

    List<UserRole> findByRoleIdInAndDeletedFlagFalse(List<Long> roleIds);

    long countByRoleIdAndDeletedFlagFalse(Long roleId);
}
