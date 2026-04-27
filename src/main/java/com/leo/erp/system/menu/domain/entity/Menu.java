package com.leo.erp.system.menu.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_menu")
public class Menu extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "menu_code", nullable = false, unique = true, length = 64)
    private String menuCode;

    @Column(name = "menu_name", nullable = false, length = 64)
    private String menuName;

    @Column(name = "parent_code", length = 64)
    private String parentCode;

    @Column(name = "route_path", length = 128)
    private String routePath;

    @Column(name = "icon", length = 64)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "menu_type", nullable = false, length = 16)
    private String menuType;

    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
