package com.leo.erp.purchase.inbound.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "po_purchase_inbound_import_batch")
public class PurchaseInboundImportBatch extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "batch_no", nullable = false, unique = true, length = 64)
    private String batchNo;

    @Column(name = "source_purchase_order_id", nullable = false)
    private Long sourcePurchaseOrderId;

    @Column(name = "source_purchase_order_no", nullable = false, length = 64)
    private String sourcePurchaseOrderNo;
}
