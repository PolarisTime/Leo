ALTER TABLE public.ct_purchase_contract_item
    ADD COLUMN material_id bigint;

ALTER TABLE public.ct_sales_contract_item
    ADD COLUMN material_id bigint;

ALTER TABLE public.po_purchase_order_item
    ADD COLUMN material_id bigint,
    ADD COLUMN warehouse_id bigint,
    ADD COLUMN batch_no_normalized character varying(64)
        GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED;

ALTER TABLE public.po_purchase_inbound
    ADD COLUMN warehouse_id bigint;

ALTER TABLE public.po_purchase_inbound_item
    ADD COLUMN material_id bigint,
    ADD COLUMN warehouse_id bigint,
    ADD COLUMN batch_no_normalized character varying(64)
        GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED;

ALTER TABLE public.po_purchase_refund_item
    ADD COLUMN material_id bigint,
    ADD COLUMN warehouse_id bigint,
    ADD COLUMN batch_no_normalized character varying(64)
        GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED;

ALTER TABLE public.so_sales_order_item
    ADD COLUMN material_id bigint,
    ADD COLUMN warehouse_id bigint,
    ADD COLUMN batch_no_normalized character varying(64)
        GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED;

ALTER TABLE public.so_sales_outbound
    ADD COLUMN warehouse_id bigint;

ALTER TABLE public.so_sales_outbound_item
    ADD COLUMN material_id bigint,
    ADD COLUMN warehouse_id bigint,
    ADD COLUMN batch_no_normalized character varying(64)
        GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED;

ALTER TABLE public.lg_freight_bill_item
    ADD COLUMN material_id bigint,
    ADD COLUMN warehouse_id bigint,
    ADD COLUMN batch_no_normalized character varying(64)
        GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED;

ALTER TABLE public.fm_invoice_issue_item
    ADD COLUMN material_id bigint,
    ADD COLUMN warehouse_id bigint,
    ADD COLUMN batch_no_normalized character varying(64)
        GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED;

ALTER TABLE public.fm_invoice_receipt_item
    ADD COLUMN material_id bigint,
    ADD COLUMN warehouse_id bigint,
    ADD COLUMN batch_no_normalized character varying(64)
        GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED;

ALTER TABLE public.st_customer_statement_item
    ADD COLUMN material_id bigint,
    ADD COLUMN warehouse_id bigint,
    ADD COLUMN batch_no_normalized character varying(64)
        GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED;

ALTER TABLE public.st_supplier_statement_item
    ADD COLUMN material_id bigint,
    ADD COLUMN warehouse_id bigint,
    ADD COLUMN batch_no_normalized character varying(64)
        GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED;

ALTER TABLE public.st_freight_statement_item
    ADD COLUMN material_id bigint,
    ADD COLUMN warehouse_id bigint,
    ADD COLUMN batch_no_normalized character varying(64)
        GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED;

COMMENT ON COLUMN public.po_purchase_order_item.material_id IS '商品内部标识，引用 md_material(id)';
COMMENT ON COLUMN public.po_purchase_order_item.warehouse_id IS '仓库内部标识，引用 md_warehouse(id)';
COMMENT ON COLUMN public.po_purchase_order_item.batch_no_normalized IS '规范化批次号，仅用于稳定库存维度';
COMMENT ON COLUMN public.po_purchase_inbound.warehouse_id IS '入库仓库内部标识，引用 md_warehouse(id)';
COMMENT ON COLUMN public.so_sales_outbound.warehouse_id IS '出库仓库内部标识，引用 md_warehouse(id)';
