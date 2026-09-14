package com.leo.erp.inventory.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 库存事务账本实体。不可变：只新增或软删，禁止修改数量、成本与金额。
 */
@Getter
@Setter
@Entity
@Table(name = "inv_transaction")
public class InventoryTransaction extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "transaction_no", nullable = false, unique = true, length = 64)
    private String transactionNo;

    @Column(name = "transaction_type", nullable = false, length = 32)
    private String transactionType;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "material_code", length = 64)
    private String materialCode;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "warehouse_name", length = 128)
    private String warehouseName;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "direction", nullable = false)
    private Short direction;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "quantity_unit", length = 8)
    private String quantityUnit;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "source_document_type", nullable = false, length = 32)
    private String sourceDocumentType;

    @Column(name = "source_document_id")
    private Long sourceDocumentId;

    @Column(name = "source_document_no", length = 64)
    private String sourceDocumentNo;

    @Column(name = "source_item_id", nullable = false)
    private Long sourceItemId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDate occurredAt;
}
