UPDATE public.so_sales_order
SET status = '交付核定'
WHERE status = '待完善';

ALTER TABLE public.so_sales_order
    ADD CONSTRAINT chk_so_status_v61
    CHECK (status IN ('草稿', '已审核', '交付核定', '完成销售')) NOT VALID;

ALTER TABLE public.so_sales_order
    VALIDATE CONSTRAINT chk_so_status_v61;

ALTER TABLE public.so_sales_order
    DROP CONSTRAINT chk_so_status;

ALTER TABLE public.so_sales_order
    RENAME CONSTRAINT chk_so_status_v61 TO chk_so_status;
