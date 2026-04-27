package com.leo.erp.contract.sales.domain.entity;

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
@Table(name = "ct_sales_contract")
public class SalesContract extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "contract_no", nullable = false, unique = true, length = 32)
    private String contractNo;

    @Column(name = "customer_name", nullable = false, length = 128)
    private String customerName;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "sign_date", nullable = false)
    private LocalDate signDate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expire_date", nullable = false)
    private LocalDate expireDate;

    @Column(name = "sales_name", nullable = false, length = 64)
    private String salesName;

    @Column(name = "total_weight", nullable = false, precision = 14, scale = 3)
    private BigDecimal totalWeight;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;

    @OneToMany(mappedBy = "salesContract", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesContractItem> items = new ArrayList<>();
}
