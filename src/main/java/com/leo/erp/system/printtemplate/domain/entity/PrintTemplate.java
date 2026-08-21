package com.leo.erp.system.printtemplate.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_print_template")
public class PrintTemplate extends AbstractAuditableEntity {

    @Id
    private Long id;

    /** JPA 乐观锁版本号，对应 V121 迁移新增的 version 列，并发更新冲突时抛 OptimisticLockException。 */
    @Version
    private Long version;

    @Column(name = "bill_type", nullable = false, length = 64)
    private String billType;

    @Column(name = "template_name", nullable = false, length = 128)
    private String templateName;

    @Column(name = "template_code", nullable = false, length = 96)
    private String templateCode;

    @Column(name = "template_html", nullable = false, columnDefinition = "TEXT")
    private String templateHtml;

    @Column(name = "template_type", nullable = false, length = 16)
    private String templateType = "COORD";

    @Column(name = "engine", nullable = false, length = 32)
    private String engine = "LODOP";

    @Column(name = "asset_ref", length = 255)
    private String assetRef;

    @Column(name = "settlement_company_id")
    private Long settlementCompanyId;

    @Column(name = "settlement_company_name", length = 128)
    private String settlementCompanyName;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "sync_mode", nullable = false, length = 16)
    private String syncMode = "MANUAL";

    @Column(name = "source_ref", length = 255)
    private String sourceRef;

    @Column(name = "source_checksum", length = 64)
    private String sourceChecksum;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;
}
