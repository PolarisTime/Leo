package com.leo.erp.statement.freight.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
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
@Table(name = "st_freight_statement")
public class FreightStatement extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Version
    private Long version;

    @Column(name = "statement_no", nullable = false, unique = true, length = 64)
    private String statementNo;

    @Column(name = "carrier_name", nullable = false, length = 128)
    private String carrierName;

    @Column(name = "carrier_code", length = 64)
    private String carrierCode;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_weight", nullable = false, precision = 14, scale = 3)
    private BigDecimal totalWeight;

    @Column(name = "total_freight", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalFreight;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "unpaid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal unpaidAmount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "sign_status", nullable = false, length = 16)
    private String signStatus;

    @Column(name = "attachment", length = 500)
    private String attachment;

    @Column(name = "remark", length = 255)
    private String remark;

    @OneToMany(mappedBy = "freightStatement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FreightStatementItem> items = new ArrayList<>();
}
