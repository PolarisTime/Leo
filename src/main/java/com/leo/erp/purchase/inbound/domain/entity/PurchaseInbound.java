package com.leo.erp.purchase.inbound.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
public class PurchaseInbound extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "inbound_no", nullable = false, unique = true, length = 64)
    private String inboundNo;

    @Column(name = "purchase_order_no", length = 256)
    private String purchaseOrderNo;

    @Column(name = "supplier_code", nullable = false, length = 64)
    private String supplierCode;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "supplier_name", nullable = false, length = 128)
    private String supplierName;

    @Column(name = "settlement_company_id")
    private Long settlementCompanyId;

    @Column(name = "settlement_company_name", length = 128)
    private String settlementCompanyName;

    @Column(name = "warehouse_name", nullable = false, length = 128)
    private String warehouseName;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "inbound_date", nullable = false)
    private LocalDate inboundDate;

    @Column(name = "settlement_mode", nullable = false, length = 32)
    private String settlementMode;

    @Column(name = "total_weight", nullable = false, precision = 18, scale = 8)
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
