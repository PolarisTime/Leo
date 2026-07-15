package com.leo.erp.logistics.bill.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "lg_freight_bill_source_order")
public class FreightBillSourceOrder extends AbstractAuditableEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freight_bill_id", nullable = false)
    private FreightBill freightBill;

    @Column(name = "source_sales_order_id", nullable = false)
    private Long sourceSalesOrderId;

    @Column(name = "source_sales_order_no", nullable = false, length = 64)
    private String sourceSalesOrderNo;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;
}
