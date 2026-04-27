package com.leo.erp.system.norule.domain.entity;

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
@Table(name = "sys_no_rule")
public class NoRule extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "setting_code", nullable = false, unique = true, length = 64)
    private String settingCode;

    @Column(name = "setting_name", nullable = false, length = 128)
    private String settingName;

    @Column(name = "bill_name", nullable = false, length = 128)
    private String billName;

    @Column(name = "prefix", nullable = false, length = 64)
    private String prefix;

    @Column(name = "date_rule", nullable = false, length = 32)
    private String dateRule;

    @Column(name = "serial_length", nullable = false)
    private Integer serialLength;

    @Column(name = "reset_rule", nullable = false, length = 32)
    private String resetRule;

    @Column(name = "sample_no", nullable = false, length = 64)
    private String sampleNo;

    @Column(name = "current_period", length = 32)
    private String currentPeriod;

    @Column(name = "next_serial_value")
    private Long nextSerialValue;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
