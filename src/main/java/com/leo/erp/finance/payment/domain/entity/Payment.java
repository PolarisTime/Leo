package com.leo.erp.finance.payment.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
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
@Table(name = "fm_payment")
public class Payment extends AuditableEntity {

    @Id
    private Long id;

    @Version
    private Long version;

    @Column(name = "payment_no", nullable = false, unique = true, length = 32)
    private String paymentNo;

    @Column(name = "business_type", nullable = false, length = 32)
    private String businessType;

    @Column(name = "counterparty_name", nullable = false, length = 128)
    private String counterpartyName;

    @Column(name = "source_statement_id")
    private Long sourceStatementId;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

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

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentAllocation> items = new ArrayList<>();

    @Transient
    private Set<Long> originalAllocationStatementIds = new LinkedHashSet<>();

    @Transient
    private String originalBusinessType;
}
