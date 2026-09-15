package com.leo.erp.security.rbac.repository;

import com.leo.erp.security.rbac.domain.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SysRoleRepository extends JpaRepository<SysRole, Long>, JpaSpecificationExecutor<SysRole> {

    Optional<SysRole> findByIdAndDeletedFlagFalse(Long id);

    Optional<SysRole> findByCodeAndDeletedFlagFalse(String code);

    boolean existsByCodeAndDeletedFlagFalse(String code);

    List<SysRole> findByIdInAndDeletedFlagFalse(Collection<Long> ids);

    @Query("SELECT r FROM SysRole r WHERE r.id IN :ids AND r.deletedFlag = false AND r.status = :status")
    List<SysRole> findActiveByIdIn(@Param("ids") Collection<Long> ids, @Param("status") String status);
}
