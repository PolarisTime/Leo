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

/**
 * 物流单行级来源占用：记录本物流单对某条销售订单明细实际占用的数量。
 * <p>草稿物流单即占用来源额度（业务决策 1A），一张物流单内不允许重复引用同一来源明细（2A）。
 */
@Getter
@Setter
@Entity
@Table(name = "lg_freight_bill_source_item")
public class FreightBillSourceItem extends AbstractAuditableEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freight_bill_id", nullable = false)
    private FreightBill freightBill;

    @Column(name = "source_sales_order_item_id", nullable = false)
    private Long sourceSalesOrderItemId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;
}
