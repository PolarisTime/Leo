package com.leo.erp.finance.supplierrefundreceipt.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "fm_supplier_refund_receipt")
public class SupplierRefundReceipt extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "refund_receipt_no", nullable = false, unique = true, length = 64)
    private String refundReceiptNo;

    @Column(name = "purchase_refund_id", nullable = false)
    private Long purchaseRefundId;

    @Column(name = "supplier_code", nullable = false, length = 64)
    private String supplierCode;

    @Column(name = "supplier_name", nullable = false, length = 128)
    private String supplierName;

    @Column(name = "settlement_company_id")
    private Long settlementCompanyId;

    @Column(name = "settlement_company_name", length = 128)
    private String settlementCompanyName;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "receipt_method", nullable = false, length = 32)
    private String receiptMethod;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "operator_name", nullable = false, length = 32)
    private String operatorName;

    @Column(name = "remark", length = 255)
    private String remark;
}
