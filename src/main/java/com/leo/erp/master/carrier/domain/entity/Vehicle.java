package com.leo.erp.master.carrier.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "md_vehicle")
public class Vehicle {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id", nullable = false)
    private Carrier carrier;

    @Column(name = "plate", nullable = false, length = 16)
    private String plate;

    @Column(name = "contact", length = 32)
    private String contact;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "remark", length = 64)
    private String remark;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
