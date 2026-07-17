package com.leo.erp.system.generalsetting.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_general_setting")
public class GeneralSetting extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "setting_code", nullable = false, unique = true, length = 64)
    private String settingCode;

    @Column(name = "setting_name", nullable = false, length = 128)
    private String settingName;

    @Column(name = "setting_group", nullable = false, length = 128)
    private String settingGroup;

    @Column(name = "setting_value", nullable = false, length = 64)
    private String settingValue;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
