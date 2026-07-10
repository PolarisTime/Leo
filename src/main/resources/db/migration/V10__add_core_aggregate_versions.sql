ALTER TABLE public.po_purchase_order
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.po_purchase_inbound
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.so_sales_order
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.so_sales_outbound
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.fm_invoice_issue
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.fm_invoice_receipt
    ADD COLUMN version bigint NOT NULL DEFAULT 0;
