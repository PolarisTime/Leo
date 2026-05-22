package com.leo.erp.finance.invoiceissue.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "fm_invoice_issue_source_order",
       uniqueConstraints = @UniqueConstraint(columnNames = {"issue_id", "sales_order_id"}))
public class InvoiceIssueSourceOrder {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private InvoiceIssue issue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_name", nullable = false, length = 64)
    private String createdName;

    @Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt;
}
