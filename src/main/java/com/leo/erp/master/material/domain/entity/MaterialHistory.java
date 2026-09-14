package com.leo.erp.master.material.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

/**
 * 商品主数据版本历史：每次变更追加一条不可变记录。
 * before/after 快照以 JSONB 存储；created_by/created_at 复用审计列作为 changed_by/changed_at。
 */
@Getter
@Setter
@Entity
@Table(name = "md_material_history")
public class MaterialHistory extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "change_source", nullable = false, length = 16)
    private String changeSource;

    @Column(name = "change_type", nullable = false, length = 16)
    private String changeType;

    @Column(name = "before_snapshot", columnDefinition = "JSONB")
    @ColumnTransformer(read = "before_snapshot::text", write = "?::jsonb")
    private String beforeSnapshot;

    @Column(name = "after_snapshot", columnDefinition = "JSONB")
    @ColumnTransformer(read = "after_snapshot::text", write = "?::jsonb")
    private String afterSnapshot;

    @Column(name = "import_batch_no", length = 64)
    private String importBatchNo;

    @Column(name = "remark", length = 255)
    private String remark;
}
