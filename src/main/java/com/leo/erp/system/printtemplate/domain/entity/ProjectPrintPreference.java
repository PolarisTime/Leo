package com.leo.erp.system.printtemplate.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 项目打印模板偏好: 按「项目 + 单据类型」记忆该项目上次打印所选模板。
 * <p>计划态引用: {@code templateId} 不加外键, 模板删除/停用时由前端回退默认模板;
 * 无项目的单据不写入偏好。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "sys_project_print_preference")
public class ProjectPrintPreference extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "bill_type", nullable = false, length = 64)
    private String billType;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "template_name", nullable = false, length = 128)
    private String templateName;
}
