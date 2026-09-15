package com.leo.erp.security.rbac.repository;

import com.leo.erp.security.rbac.domain.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysPermissionRepository extends JpaRepository<SysPermission, String> {
}
