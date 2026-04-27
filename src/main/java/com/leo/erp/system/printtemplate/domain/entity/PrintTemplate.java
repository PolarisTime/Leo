package com.leo.erp.system.printtemplate.domain.entity;

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
@Table(name = "sys_print_template")
public class PrintTemplate extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "bill_type", nullable = false, length = 64)
    private String billType;

    @Column(name = "template_name", nullable = false, length = 128)
    private String templateName;

    @Column(name = "template_html", nullable = false, columnDefinition = "TEXT")
    private String templateHtml;

    @Column(name = "is_default", nullable = false, length = 1)
    private String isDefault;
}
