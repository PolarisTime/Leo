ALTER TABLE public.so_sales_outbound
    DROP CONSTRAINT IF EXISTS chk_outbound_status;

ALTER TABLE public.so_sales_outbound
    ADD CONSTRAINT chk_outbound_status
    CHECK (
        (status)::text = ANY (
            ARRAY[
                '草稿'::character varying,
                '预出库'::character varying,
                '已审核'::character varying
            ]::text[]
        )
    ) NOT VALID;

ALTER TABLE public.so_sales_outbound
    VALIDATE CONSTRAINT chk_outbound_status;
