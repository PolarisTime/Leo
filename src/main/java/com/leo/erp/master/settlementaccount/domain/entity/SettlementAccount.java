package com.leo.erp.master.settlementaccount.domain.entity;

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
@Table(name = "md_settlement_account")
public class SettlementAccount extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "account_name", nullable = false, length = 128)
    private String accountName;

    @Column(name = "company_name", nullable = false, length = 128)
    private String companyName;

    @Column(name = "bank_name", nullable = false, length = 128)
    private String bankName;

    @Column(name = "bank_account", nullable = false, unique = true, length = 64)
    private String bankAccount;

    @Column(name = "usage_type", nullable = false, length = 32)
    private String usageType;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
