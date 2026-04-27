package com.leo.erp.sales.outbound.domain.entity;

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
@Table(name = "so_sales_outbound")
public class SalesOutbound extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "outbound_no", nullable = false, unique = true, length = 32)
    private String outboundNo;

    @Column(name = "sales_order_no", length = 256)
    private String salesOrderNo;

    @Column(name = "customer_name", nullable = false, length = 128)
    private String customerName;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "warehouse_name", nullable = false, length = 128)
    private String warehouseName;

    @Column(name = "outbound_date", nullable = false)
    private LocalDate outboundDate;

    @Column(name = "total_weight", nullable = false, precision = 14, scale = 3)
    private BigDecimal totalWeight;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;

    @OneToMany(mappedBy = "salesOutbound", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesOutboundItem> items = new ArrayList<>();
}
