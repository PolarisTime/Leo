package com.leo.erp.finance.invoicereceipt.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "fm_invoice_receipt_source_order",
       uniqueConstraints = @UniqueConstraint(columnNames = {"receipt_id", "purchase_order_id"}))
public class InvoiceReceiptSourceOrder {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private InvoiceReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_name", nullable = false, length = 64)
    private String createdName;

    @Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt;
}
