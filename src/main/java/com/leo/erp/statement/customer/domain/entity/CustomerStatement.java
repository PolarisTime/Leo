package com.leo.erp.statement.customer.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
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
@Table(name = "st_customer_statement")
public class CustomerStatement extends AuditableEntity {

    @Id
    private Long id;

    @Version
    private Long version;

    @Column(name = "statement_no", nullable = false, unique = true, length = 32)
    private String statementNo;

    @Column(name = "source_order_nos", length = 500)
    private String sourceOrderNos;

    @Column(name = "customer_name", nullable = false, length = 128)
    private String customerName;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "sales_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal salesAmount;

    @Column(name = "receipt_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal receiptAmount;

    @Column(name = "closing_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal closingAmount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;

    @OneToMany(mappedBy = "customerStatement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerStatementItem> items = new ArrayList<>();
}
