package com.leo.erp.purchase.inbound.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "po_purchase_inbound")
public class PurchaseInbound extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "inbound_no", nullable = false, unique = true, length = 32)
    private String inboundNo;

    @Column(name = "purchase_order_no", length = 256)
    private String purchaseOrderNo;

    @Column(name = "supplier_name", nullable = false, length = 128)
    private String supplierName;

    @Column(name = "warehouse_name", nullable = false, length = 128)
    private String warehouseName;

    @Column(name = "inbound_date", nullable = false)
    private LocalDate inboundDate;

    @Column(name = "settlement_mode", nullable = false, length = 32)
    private String settlementMode;

    @Column(name = "total_weight", nullable = false, precision = 14, scale = 3)
    private BigDecimal totalWeight;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;

    @OneToMany(mappedBy = "purchaseInbound", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseInboundItem> items = new ArrayList<>();
}
