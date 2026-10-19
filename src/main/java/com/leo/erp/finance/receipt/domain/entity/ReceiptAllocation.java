package com.leo.erp.finance.receipt.domain.entity;

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
@Table(name = "fm_receipt_allocation")
public class ReceiptAllocation {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    /**
     * 来源客户对账单 id。
     *
     * <p>历史上有两列（source_statement_id / source_customer_statement_id）并存，
     * 前者是早期不带语义前缀的写法。已通过 V192 迁移收敛到本列并删除旧列，
     * 不要再新增别名访问器 —— 重复声明让「该写哪一列」长期含糊。</p>
     */
    @Column(name = "source_customer_statement_id", nullable = false)
    private Long sourceCustomerStatementId;

    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal allocatedAmount;
}
