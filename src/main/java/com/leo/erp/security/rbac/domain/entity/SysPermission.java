package com.leo.erp.security.rbac.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * RBAC0 权限目录项，由 {@code PermissionCatalogSyncService} 按
 * {@code PermissionCodes.all()} 幂等同步。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_permission")
public class SysPermission {

    @Id
    @Column(name = "code", nullable = false, length = 128)
    private String code;

    @Column(name = "resource", nullable = false, length = 64)
    private String resource;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "field", length = 64)
    private String field;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
