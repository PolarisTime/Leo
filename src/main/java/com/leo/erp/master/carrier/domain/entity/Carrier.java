package com.leo.erp.master.carrier.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "md_carrier")
public class Carrier extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "carrier_code", nullable = false, unique = true, length = 64)
    private String carrierCode;

    @Column(name = "carrier_name", nullable = false, length = 128)
    private String carrierName;

    @Column(name = "contact_name", length = 32)
    private String contactName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @OneToMany(mappedBy = "carrier", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Vehicle> vehicles = new ArrayList<>();

    @Column(name = "vehicle_type", length = 64)
    private String vehicleType;

    @Column(name = "price_mode", length = 32)
    private String priceMode;

    @Column(name = "default_settlement_company_id")
    private Long defaultSettlementCompanyId;

    @Column(name = "default_settlement_company_name", length = 128)
    private String defaultSettlementCompanyName;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
