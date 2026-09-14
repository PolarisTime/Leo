-- 商品主数据版本历史：记录主数据变更前后快照，支持审计与导入批次回滚。
-- 纯新增表，不修改既有 md_material 结构与历史数据。

CREATE TABLE public.md_material_history (
    id bigint NOT NULL,
    material_id bigint NOT NULL,
    change_source character varying(16) NOT NULL,
    change_type character varying(16) NOT NULL,
    before_snapshot jsonb,
    after_snapshot jsonb,
    import_batch_no character varying(64),
    remark character varying(255),
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT md_material_history_pkey PRIMARY KEY (id),
    CONSTRAINT fk_md_material_history_material FOREIGN KEY (material_id) REFERENCES public.md_material (id),
    CONSTRAINT chk_md_material_history_source CHECK (change_source IN ('MANUAL', 'IMPORT', 'ROLLBACK')),
    CONSTRAINT chk_md_material_history_type CHECK (change_type IN ('CREATED', 'UPDATED', 'DELETED', 'ROLLBACK'))
);

COMMENT ON TABLE public.md_material_history IS '商品主数据版本历史：每次变更追加一条不可变记录，含前后字段快照、来源与导入批次，用于审计与按批次回滚。';
COMMENT ON COLUMN public.md_material_history.material_id IS '商品ID，关联 md_material.id。';
COMMENT ON COLUMN public.md_material_history.change_source IS '变更来源：MANUAL 手工保存 / IMPORT 批量导入 / ROLLBACK 批次回滚。';
COMMENT ON COLUMN public.md_material_history.change_type IS '变更类型：CREATED 新建 / UPDATED 更新 / DELETED 删除 / ROLLBACK 回滚。';
COMMENT ON COLUMN public.md_material_history.before_snapshot IS '变更前字段快照(JSON)，新建时为 NULL。';
COMMENT ON COLUMN public.md_material_history.after_snapshot IS '变更后字段快照(JSON)。';
COMMENT ON COLUMN public.md_material_history.import_batch_no IS '导入批次号，仅 IMPORT 来源有值，用于按批次回滚。';
COMMENT ON COLUMN public.md_material_history.remark IS '备注，回滚等场景记录说明。';
COMMENT ON COLUMN public.md_material_history.deleted_flag IS '逻辑删除标记，历史记录不删除。';

CREATE INDEX idx_md_material_history_material_id ON public.md_material_history (material_id);
CREATE INDEX idx_md_material_history_batch_no ON public.md_material_history (import_batch_no);
CREATE INDEX idx_md_material_history_created_at ON public.md_material_history (created_at);
