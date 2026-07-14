package com.leo.erp.finance.receipt.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "fm_receipt")
public class Receipt extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Version
    private Long version;

    @Column(name = "receipt_no", nullable = false, unique = true, length = 64)
    private String receiptNo;

    @Column(name = "counterparty_type", nullable = false, length = 32)
    private String counterpartyType;

    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    @Column(name = "counterparty_code", length = 64)
    private String counterpartyCode;

    @Column(name = "counterparty_name", nullable = false, length = 128)
    private String counterpartyName;

    @Column(name = "receipt_purpose", nullable = false, length = 32)
    private String receiptPurpose;

    @Column(name = "customer_name", length = 128)
    private String customerName;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "source_customer_statement_id")
    private Long sourceCustomerStatementId;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "pay_type", nullable = false, length = 32)
    private String payType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "customer_code", length = 64)
    private String customerCode;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "settlement_company_id")
    private Long settlementCompanyId;

    @Column(name = "settlement_company_name", length = 128)
    private String settlementCompanyName;

    @Column(name = "operator_name", nullable = false, length = 32)
    private String operatorName;

    @Column(name = "remark", length = 255)
    private String remark;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceiptAllocation> items = new ArrayList<>();

    @Transient
    private Set<Long> originalAllocationStatementIds = new LinkedHashSet<>();

    @Deprecated(forRemoval = false)
    public Long getSourceStatementId() {
        return sourceCustomerStatementId;
    }

    @Deprecated(forRemoval = false)
    public void setSourceStatementId(Long sourceStatementId) {
        this.sourceCustomerStatementId = sourceStatementId;
    }
}
