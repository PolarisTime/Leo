package com.leo.erp.master.customer.domain.entity;

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
@Table(name = "md_customer")
public class Customer extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "customer_code", nullable = false, unique = true, length = 64)
    private String customerCode;

    @Column(name = "customer_name", nullable = false, length = 128)
    private String customerName;

    @Column(name = "contact_name", nullable = false, length = 64)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 32)
    private String contactPhone;

    @Column(name = "city", nullable = false, length = 64)
    private String city;

    @Column(name = "settlement_mode", nullable = false, length = 32)
    private String settlementMode;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
