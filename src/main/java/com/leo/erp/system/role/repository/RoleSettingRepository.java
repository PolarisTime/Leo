package com.leo.erp.system.role.repository;

import com.leo.erp.system.role.domain.entity.RoleSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleSettingRepository extends JpaRepository<RoleSetting, Long>, JpaSpecificationExecutor<RoleSetting> {

    boolean existsByRoleCodeAndDeletedFlagFalse(String roleCode);

    Optional<RoleSetting> findByRoleCodeAndDeletedFlagFalse(String roleCode);

    List<RoleSetting> findByIdInAndDeletedFlagFalse(Collection<Long> ids);

    List<RoleSetting> findByRoleCodeInAndDeletedFlagFalse(Collection<String> roleCodes);

    List<RoleSetting> findByRoleNameInAndDeletedFlagFalse(Collection<String> roleNames);

    Optional<RoleSetting> findByIdAndDeletedFlagFalse(Long id);
}
