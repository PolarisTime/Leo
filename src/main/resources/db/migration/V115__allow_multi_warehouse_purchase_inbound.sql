-- Multi-warehouse documents keep the authoritative warehouse identity on each line.
ALTER TABLE public.po_purchase_inbound
    ALTER COLUMN warehouse_id DROP NOT NULL;
