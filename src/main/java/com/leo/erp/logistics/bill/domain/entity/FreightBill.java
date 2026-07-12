package com.leo.erp.logistics.bill.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "lg_freight_bill")
public class FreightBill extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "bill_no", nullable = false, unique = true, length = 64)
    private String billNo;

    @Column(name = "carrier_id")
    private Long carrierId;

    @Column(name = "carrier_code", nullable = false, length = 64)
    private String carrierCode;

    @Column(name = "carrier_name", nullable = false, length = 128)
    private String carrierName;

    @Column(name = "settlement_company_id")
    private Long settlementCompanyId;

    @Column(name = "settlement_company_name", length = 128)
    private String settlementCompanyName;

    @Column(name = "vehicle_plate", length = 16)
    private String vehiclePlate;

    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Column(name = "customer_name", nullable = false, length = 128)
    private String customerName;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "bill_time", nullable = false)
    private LocalDate billTime;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_weight", nullable = false, precision = 18, scale = 8)
    private BigDecimal totalWeight;

    @Column(name = "total_freight", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalFreight;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;

    @OneToMany(mappedBy = "freightBill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FreightBillItem> items = new ArrayList<>();
}
