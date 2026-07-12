ALTER TABLE public.ct_purchase_contract
    ADD COLUMN supplier_id bigint,
    ADD COLUMN supplier_code character varying(64);

ALTER TABLE public.po_purchase_order
    ADD COLUMN supplier_id bigint;

ALTER TABLE public.po_purchase_inbound
    ADD COLUMN supplier_id bigint;

ALTER TABLE public.po_purchase_refund
    ADD COLUMN supplier_id bigint;

ALTER TABLE public.fm_invoice_receipt
    ADD COLUMN supplier_id bigint;

ALTER TABLE public.st_supplier_statement
    ADD COLUMN supplier_id bigint;

ALTER TABLE public.fm_supplier_refund_receipt
    ADD COLUMN supplier_id bigint;

COMMENT ON COLUMN public.ct_purchase_contract.supplier_id IS '供应商内部标识，引用 md_supplier(id)';
COMMENT ON COLUMN public.po_purchase_order.supplier_id IS '供应商内部标识，引用 md_supplier(id)';
COMMENT ON COLUMN public.po_purchase_inbound.supplier_id IS '供应商内部标识，引用 md_supplier(id)';
COMMENT ON COLUMN public.po_purchase_refund.supplier_id IS '供应商内部标识，引用 md_supplier(id)';
COMMENT ON COLUMN public.fm_invoice_receipt.supplier_id IS '供应商内部标识，引用 md_supplier(id)';
COMMENT ON COLUMN public.st_supplier_statement.supplier_id IS '供应商内部标识，引用 md_supplier(id)';
COMMENT ON COLUMN public.fm_supplier_refund_receipt.supplier_id IS '供应商内部标识，引用 md_supplier(id)';
