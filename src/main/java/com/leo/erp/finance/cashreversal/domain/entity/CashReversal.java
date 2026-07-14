package com.leo.erp.finance.cashreversal.domain.entity;

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
@Table(name = "fm_cash_reversal")
public class CashReversal extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Version
    private Long version;

    @Column(name = "reversal_no", nullable = false, unique = true, length = 64)
    private String reversalNo;

    @Column(name = "original_payment_id")
    private Long originalPaymentId;

    @Column(name = "original_receipt_id")
    private Long originalReceiptId;

    @Column(name = "counterparty_type", nullable = false, length = 32)
    private String counterpartyType;

    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    @Column(name = "counterparty_code", length = 64)
    private String counterpartyCode;

    @Column(name = "counterparty_name", nullable = false, length = 128)
    private String counterpartyName;

    @Column(name = "settlement_company_id", nullable = false)
    private Long settlementCompanyId;

    @Column(name = "settlement_company_name", nullable = false, length = 128)
    private String settlementCompanyName;

    @Column(name = "reversal_date", nullable = false)
    private LocalDate reversalDate;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "operator_name", nullable = false, length = 32)
    private String operatorName;

    @Column(name = "remark", length = 255)
    private String remark;
}
