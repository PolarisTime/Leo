-- Multi-warehouse documents keep the authoritative warehouse identity on each line.
ALTER TABLE public.so_sales_outbound
    ALTER COLUMN warehouse_id DROP NOT NULL;
