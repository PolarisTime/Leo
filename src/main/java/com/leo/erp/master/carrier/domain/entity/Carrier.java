package com.leo.erp.master.carrier.domain.entity;

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
@Table(name = "md_carrier")
public class Carrier extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "carrier_code", nullable = false, unique = true, length = 64)
    private String carrierCode;

    @Column(name = "carrier_name", nullable = false, length = 128)
    private String carrierName;

    @Column(name = "contact_name", length = 64)
    private String contactName;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "vehicle_type", length = 64)
    private String vehicleType;

    @Column(name = "vehicle_plates", columnDefinition = "TEXT")
    private String vehiclePlates;

    @Column(name = "vehicle_plate", length = 16)
    private String vehiclePlate;

    @Column(name = "vehicle_contact", length = 64)
    private String vehicleContact;

    @Column(name = "vehicle_phone", length = 32)
    private String vehiclePhone;

    @Column(name = "vehicle_plate2", length = 16)
    private String vehiclePlate2;

    @Column(name = "vehicle_contact2", length = 64)
    private String vehicleContact2;

    @Column(name = "vehicle_phone2", length = 32)
    private String vehiclePhone2;

    @Column(name = "vehicle_plate3", length = 16)
    private String vehiclePlate3;

    @Column(name = "vehicle_contact3", length = 64)
    private String vehicleContact3;

    @Column(name = "vehicle_phone3", length = 32)
    private String vehiclePhone3;

    @Column(name = "vehicle_remark", length = 64)
    private String vehicleRemark;

    @Column(name = "vehicle_remark2", length = 64)
    private String vehicleRemark2;

    @Column(name = "vehicle_remark3", length = 64)
    private String vehicleRemark3;

    @Column(name = "price_mode", length = 32)
    private String priceMode;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
