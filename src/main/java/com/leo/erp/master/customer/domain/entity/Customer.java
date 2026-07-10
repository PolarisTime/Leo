package com.leo.erp.master.customer.domain.entity;

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
@Table(name = "md_customer")
public class Customer extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "customer_code", nullable = false, length = 64)
    private String customerCode;

    @Column(name = "customer_name", nullable = false, length = 128)
    private String customerName;

    @Column(name = "contact_name", length = 32)
    private String contactName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "city", length = 64)
    private String city;

    @Column(name = "settlement_mode", length = 32)
    private String settlementMode;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "project_name_abbr", length = 64)
    private String projectNameAbbr;

    @Column(name = "project_address", length = 255)
    private String projectAddress;

    @Column(name = "default_settlement_company_id")
    private Long defaultSettlementCompanyId;

    @Column(name = "default_settlement_company_name", length = 128)
    private String defaultSettlementCompanyName;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
