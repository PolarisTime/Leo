package com.leo.erp.statement.supplier.domain.entity;

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
@Table(name = "st_supplier_statement")
public class SupplierStatement extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "statement_no", nullable = false, unique = true, length = 32)
    private String statementNo;

    @Column(name = "source_inbound_nos", length = 500)
    private String sourceInboundNos;

    @Column(name = "supplier_name", nullable = false, length = 128)
    private String supplierName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "purchase_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal purchaseAmount;

    @Column(name = "payment_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "closing_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal closingAmount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;

    @OneToMany(mappedBy = "supplierStatement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SupplierStatementItem> items = new ArrayList<>();
}
