CREATE TABLE public.po_purchase_inbound_import_batch (
    id bigint PRIMARY KEY,
    batch_no character varying(64) NOT NULL UNIQUE,
    source_purchase_order_id bigint NOT NULL,
    source_purchase_order_no character varying(64) NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system' NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT chk_purchase_inbound_import_batch_no
        CHECK (NULLIF(BTRIM(batch_no), '') IS NOT NULL),
    CONSTRAINT chk_purchase_inbound_import_batch_source_no
        CHECK (NULLIF(BTRIM(source_purchase_order_no), '') IS NOT NULL),
    CONSTRAINT fk_purchase_inbound_import_batch_order
        FOREIGN KEY (source_purchase_order_id)
        REFERENCES public.po_purchase_order (id)
        ON DELETE RESTRICT
);

ALTER TABLE public.po_purchase_inbound
    ADD COLUMN import_batch_id bigint,
    ADD CONSTRAINT fk_purchase_inbound_import_batch
        FOREIGN KEY (import_batch_id)
        REFERENCES public.po_purchase_inbound_import_batch (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_purchase_inbound_import_batch_order
    ON public.po_purchase_inbound_import_batch (source_purchase_order_id)
    WHERE deleted_flag = false;

CREATE INDEX idx_purchase_inbound_import_batch_id
    ON public.po_purchase_inbound (import_batch_id)
    WHERE deleted_flag = false;

COMMENT ON TABLE public.po_purchase_inbound_import_batch IS
    '一次采购订单导入原子生成的采购入库草稿批次';
COMMENT ON COLUMN public.po_purchase_inbound.import_batch_id IS
    '采购入库拆分导入批次ID，历史手工入库允许为空';
