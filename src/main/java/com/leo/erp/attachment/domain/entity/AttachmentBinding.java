package com.leo.erp.attachment.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_attachment_binding")
public class AttachmentBinding extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "module_key", nullable = false, length = 64)
    private String moduleKey;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "attachment_id", nullable = false)
    private Long attachmentId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
