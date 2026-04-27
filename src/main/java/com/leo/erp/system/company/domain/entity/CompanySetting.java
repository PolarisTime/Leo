package com.leo.erp.system.company.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "sys_company_setting")
public class CompanySetting extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "company_name", nullable = false, unique = true, length = 128)
    private String companyName;

    @Column(name = "tax_no", nullable = false, length = 64)
    private String taxNo;

    @Column(name = "bank_name", nullable = false, length = 128)
    private String bankName;

    @Column(name = "bank_account", nullable = false, length = 64)
    private String bankAccount;

    @Column(name = "tax_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "settlement_accounts_json", nullable = false, columnDefinition = "TEXT")
    private String settlementAccountsJson;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
