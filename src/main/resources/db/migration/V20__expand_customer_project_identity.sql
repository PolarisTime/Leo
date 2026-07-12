ALTER TABLE public.md_project
    ADD COLUMN customer_id bigint;

COMMENT ON COLUMN public.md_project.customer_id IS
    '客户内部标识，引用 md_customer(id)；Expand 阶段仅新增可空列';

ALTER TABLE public.so_sales_order
    ADD COLUMN customer_id bigint;

COMMENT ON COLUMN public.so_sales_order.customer_id IS
    '客户内部标识，引用 md_customer(id)；客户编码和名称继续作为历史快照';
