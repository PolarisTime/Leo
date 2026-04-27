package com.leo.erp.finance.invoiceissue.domain.entity;

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
@Table(name = "fm_invoice_issue")
public class InvoiceIssue extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "issue_no", nullable = false, unique = true, length = 32)
    private String issueNo;

    @Column(name = "invoice_no", nullable = false, length = 64)
    private String invoiceNo;

    @Column(name = "source_sales_order_nos", length = 2000)
    private String sourceSalesOrderNos;

    @Column(name = "customer_name", nullable = false, length = 128)
    private String customerName;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "invoice_type", nullable = false, length = 32)
    private String invoiceType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operatorName;

    @Column(name = "remark", length = 255)
    private String remark;

    @OneToMany(mappedBy = "invoiceIssue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceIssueItem> items = new ArrayList<>();
}
