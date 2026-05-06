package com.leo.erp.finance.payment.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "fm_payment_allocation")
public class PaymentAllocation {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "source_statement_id", nullable = false)
    private Long sourceStatementId;

    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal allocatedAmount;
}
