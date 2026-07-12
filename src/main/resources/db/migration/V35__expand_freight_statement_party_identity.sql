ALTER TABLE public.st_freight_statement_item
    ADD COLUMN customer_id bigint,
    ADD COLUMN project_id bigint;

COMMENT ON COLUMN public.st_freight_statement_item.customer_id IS
    '本行客户内部标识，引用 md_customer(id)';
COMMENT ON COLUMN public.st_freight_statement_item.project_id IS
    '本行项目内部标识，引用 md_project(id)';
