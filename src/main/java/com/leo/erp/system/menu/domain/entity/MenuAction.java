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
@Table(name = "sys_menu_action")
public class MenuAction extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "menu_code", nullable = false, length = 64)
    private String menuCode;

    @Column(name = "action_code", nullable = false, length = 32)
    private String actionCode;

    @Column(name = "action_name", nullable = false, length = 32)
    private String actionName;
}
