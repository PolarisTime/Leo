package com.leo.erp.purchase.order.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.persistence.StatusAwareEntity;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "po_purchase_order")
public class PurchaseOrder extends AbstractAuditableEntity implements StatusAwareEntity {

    @Id
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "order_no", nullable = false, unique = true, length = 64)
    private String orderNo;

    @Column(name = "supplier_code", nullable = false, length = 64)
    private String supplierCode;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "supplier_name", nullable = false, length = 128)
    private String supplierName;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "buyer_name", length = 32)
    private String buyerName;

    @Column(name = "settlement_company_id")
    private Long settlementCompanyId;

    @Column(name = "settlement_company_name", length = 128)
    private String settlementCompanyName;

    @Column(name = "total_weight", nullable = false, precision = 18, scale = 8)
    private BigDecimal totalWeight;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;

    /**
     * 强制结单标记: 剩余未入库件数作废, 订单由人工置为完成采购(区别于入库审核自动完成)。
     * <p>撤销结单时连同下列留痕字段一起清空。</p>
     */
    @Column(name = "force_closed", nullable = false)
    private boolean forceClosed = false;

    @Column(name = "force_close_reason", length = 255)
    private String forceCloseReason;

    /** 结单时的未入库件数快照(即本次作废件数)。 */
    @Column(name = "force_close_remaining_quantity")
    private Integer forceCloseRemainingQuantity;

    @Column(name = "force_closed_by")
    private Long forceClosedBy;

    @Column(name = "force_closed_name", length = 64)
    private String forceClosedName;

    @Column(name = "force_closed_at")
    private LocalDateTime forceClosedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items = new ArrayList<>();
}
