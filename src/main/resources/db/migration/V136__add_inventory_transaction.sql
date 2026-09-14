-- Phase 5 库存台账（三账齐动）：不可变库存事务账本。
-- 由采购入库 / 销售出库 / 销售退货的审核、反审核、删除驱动，只新增或软删，不修改数量。
-- 余额不落表，查询时按 (material_id, warehouse_id) 聚合；金额采用带符号 amount，
-- 约定 amount = direction * quantity * unit_cost，便于直接 SUM(amount) 得到库存价值余额。

CREATE TABLE public.inv_transaction (
    id bigint NOT NULL,
    transaction_no character varying(64) NOT NULL,
    transaction_type character varying(32) NOT NULL,
    material_id bigint NOT NULL,
    material_code character varying(64),
    warehouse_id bigint,
    warehouse_name character varying(128),
    batch_no character varying(64),
    direction smallint NOT NULL,
    quantity integer NOT NULL,
    quantity_unit character varying(8),
    unit_cost numeric(12,2) NOT NULL,
    amount numeric(14,2) NOT NULL,
    source_document_type character varying(32) NOT NULL,
    source_document_id bigint,
    source_document_no character varying(64),
    source_item_id bigint NOT NULL,
    occurred_at date NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT inv_transaction_pkey PRIMARY KEY (id),
    CONSTRAINT uk_inv_transaction_no UNIQUE (transaction_no),
    CONSTRAINT chk_inv_transaction_direction CHECK (direction IN (1, -1)),
    CONSTRAINT chk_inv_transaction_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_inv_transaction_type CHECK (transaction_type IN (
        'PURCHASE_IN',
        'SALES_OUT',
        'SALES_RETURN_IN',
        'PURCHASE_RETURN_OUT',
        'TRANSFER',
        'COUNT_ADJUST'
    )),
    CONSTRAINT chk_inv_transaction_source_type CHECK (source_document_type IN (
        'PURCHASE_INBOUND',
        'SALES_OUTBOUND',
        'SALES_RETURN'
    ))
);

COMMENT ON TABLE public.inv_transaction IS
    '库存事务账本：不可变库存与价值流水，由采购入库/销售出库/销售退货审核驱动，只新增或软删。';
COMMENT ON COLUMN public.inv_transaction.transaction_no IS '库存事务号，默认取雪花ID字符串。';
COMMENT ON COLUMN public.inv_transaction.transaction_type IS
    '事务类型：PURCHASE_IN/SALES_OUT/SALES_RETURN_IN/PURCHASE_RETURN_OUT/TRANSFER/COUNT_ADJUST。';
COMMENT ON COLUMN public.inv_transaction.material_id IS '物料ID（雪花ID）。';
COMMENT ON COLUMN public.inv_transaction.material_code IS '物料编码快照。';
COMMENT ON COLUMN public.inv_transaction.warehouse_id IS '仓库ID（雪花ID），来源明细缺仓库时为空，余额按物料聚合。';
COMMENT ON COLUMN public.inv_transaction.warehouse_name IS '仓库名称快照。';
COMMENT ON COLUMN public.inv_transaction.batch_no IS '批次号快照，可为空。';
COMMENT ON COLUMN public.inv_transaction.direction IS '方向：1=增加库存，-1=减少库存。';
COMMENT ON COLUMN public.inv_transaction.quantity IS '事务数量，恒为正数，方向由 direction 表示。';
COMMENT ON COLUMN public.inv_transaction.quantity_unit IS '数量单位快照。';
COMMENT ON COLUMN public.inv_transaction.unit_cost IS '本事务单位成本（入账时点的移动加权平均或来源单价），恒为正数。';
COMMENT ON COLUMN public.inv_transaction.amount IS
    '带符号金额，约定 amount = direction * quantity * unit_cost，SUM(amount) 即价值余额。';
COMMENT ON COLUMN public.inv_transaction.source_document_type IS
    '来源单据类型：PURCHASE_INBOUND/SALES_OUTBOUND/SALES_RETURN。';
COMMENT ON COLUMN public.inv_transaction.source_document_id IS '来源单据ID（雪花ID）。';
COMMENT ON COLUMN public.inv_transaction.source_document_no IS '来源单据号快照。';
COMMENT ON COLUMN public.inv_transaction.source_item_id IS '来源单据明细ID（雪花ID），幂等唯一键组成部分。';
COMMENT ON COLUMN public.inv_transaction.occurred_at IS '业务发生日期，取来源单据日期。';

CREATE INDEX idx_inv_transaction_material_warehouse
    ON public.inv_transaction (material_id, warehouse_id);
CREATE INDEX idx_inv_transaction_source_item
    ON public.inv_transaction (source_document_type, source_item_id);
CREATE INDEX idx_inv_transaction_occurred_at
    ON public.inv_transaction (occurred_at);

-- 幂等唯一：同一来源明细同一事务类型在未删除状态下只允许一条。
-- 采用部分唯一索引而非全表唯一约束，原因是反审核会软删事务、重新审核需按最新数量重新记账，
-- 全表唯一约束会阻止重新插入，且复活旧行会篡改历史数量，违反“只增/软删、不改数量”。
CREATE UNIQUE INDEX uk_inv_transaction_source_active
    ON public.inv_transaction (source_document_type, source_item_id, transaction_type)
    WHERE deleted_flag = false;
