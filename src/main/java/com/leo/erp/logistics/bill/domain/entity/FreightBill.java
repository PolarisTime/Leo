package com.leo.erp.logistics.bill.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
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
public class FreightBill extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "bill_no", nullable = false, unique = true, length = 32)
    private String billNo;

    @Column(name = "outbound_no", length = 256)
    private String outboundNo;

    @Column(name = "carrier_name", nullable = false, length = 128)
    private String carrierName;

    @Column(name = "customer_name", nullable = false, length = 128)
    private String customerName;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "bill_time", nullable = false)
    private LocalDate billTime;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_weight", nullable = false, precision = 14, scale = 3)
    private BigDecimal totalWeight;

    @Column(name = "total_freight", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalFreight;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "delivery_status", nullable = false, length = 16)
    private String deliveryStatus;

    @Column(name = "remark", length = 255)
    private String remark;

    @OneToMany(mappedBy = "freightBill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FreightBillItem> items = new ArrayList<>();
}
