package com.leo.erp.finance.ledgeradjustment.domain.entity;

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
@Table(name = "fm_ledger_adjustment")
public class LedgerAdjustment extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Version
    private Long version;

    @Column(name = "adjustment_no", nullable = false, unique = true, length = 64)
    private String adjustmentNo;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "counterparty_type", nullable = false, length = 32)
    private String counterpartyType;

    @Column(name = "counterparty_code", nullable = false, length = 64)
    private String counterpartyCode;

    @Column(name = "counterparty_name", nullable = false, length = 128)
    private String counterpartyName;

    @Column(name = "settlement_company_id")
    private Long settlementCompanyId;

    @Column(name = "settlement_company_name", length = 128)
    private String settlementCompanyName;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "adjustment_date", nullable = false)
    private LocalDate adjustmentDate;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "adjustment_type", nullable = false, length = 32)
    private String adjustmentType;

    @Column(name = "effect", nullable = false, length = 32)
    private String effect;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "operator_name", nullable = false, length = 32)
    private String operatorName;

    @Column(name = "remark", length = 255)
    private String remark;
}
