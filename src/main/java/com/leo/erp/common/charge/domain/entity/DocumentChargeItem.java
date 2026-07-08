package com.leo.erp.common.charge.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "bd_document_charge_item")
public class DocumentChargeItem extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "module_key", nullable = false, length = 64)
    private String moduleKey;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "charge_name", nullable = false, length = 64)
    private String chargeName;

    @Column(name = "charge_direction", nullable = false, length = 32)
    private String chargeDirection;

    @Column(name = "settlement_party_type", length = 32)
    private String settlementPartyType;

    @Column(name = "settlement_party_id")
    private Long settlementPartyId;

    @Column(name = "settlement_party_name", length = 128)
    private String settlementPartyName;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "billable", nullable = false)
    private boolean billable = true;

    @Column(name = "source_module_key", length = 64)
    private String sourceModuleKey;

    @Column(name = "source_document_id")
    private Long sourceDocumentId;

    @Column(name = "source_charge_item_id")
    private Long sourceChargeItemId;

    @Column(name = "remark", length = 255)
    private String remark;

    @Version
    @Column(name = "version")
    private Long version;
}
