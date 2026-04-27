package com.leo.erp.finance.receipt.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "fm_receipt")
public class Receipt extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "receipt_no", nullable = false, unique = true, length = 32)
    private String receiptNo;

    @Column(name = "customer_name", nullable = false, length = 128)
    private String customerName;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "source_customer_statement_id")
    private Long sourceStatementId;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "pay_type", nullable = false, length = 32)
    private String payType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operatorName;

    @Column(name = "remark", length = 255)
    private String remark;

    @Transient
    private Long originalSourceStatementId;

    @PostLoad
    @PostPersist
    @PostUpdate
    void captureOriginalSourceStatementId() {
        originalSourceStatementId = sourceStatementId;
    }
}
