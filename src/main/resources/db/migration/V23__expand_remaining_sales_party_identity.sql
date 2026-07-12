ALTER TABLE public.ct_sales_contract
    ADD COLUMN customer_id bigint,
    ADD COLUMN customer_code character varying(64),
    ADD COLUMN project_id bigint;

ALTER TABLE public.so_sales_outbound
    ADD COLUMN customer_id bigint,
    ADD COLUMN project_id bigint;

ALTER TABLE public.fm_invoice_issue
    ADD COLUMN customer_id bigint,
    ADD COLUMN project_id bigint;

ALTER TABLE public.st_customer_statement
    ADD COLUMN customer_id bigint;

ALTER TABLE public.st_customer_statement_item
    ADD COLUMN customer_id bigint;

ALTER TABLE public.fm_receipt
    ADD COLUMN customer_id bigint;

ALTER TABLE public.lg_freight_bill_item
    ADD COLUMN customer_id bigint,
    ADD COLUMN project_id bigint;

COMMENT ON COLUMN public.ct_sales_contract.customer_id IS '客户内部标识，引用 md_customer(id)';
COMMENT ON COLUMN public.ct_sales_contract.project_id IS '项目内部标识，引用 md_project(id)';
COMMENT ON COLUMN public.so_sales_outbound.customer_id IS '客户内部标识，引用 md_customer(id)';
COMMENT ON COLUMN public.so_sales_outbound.project_id IS '项目内部标识，引用 md_project(id)';
COMMENT ON COLUMN public.fm_invoice_issue.customer_id IS '客户内部标识，引用 md_customer(id)';
COMMENT ON COLUMN public.fm_invoice_issue.project_id IS '项目内部标识，引用 md_project(id)';
COMMENT ON COLUMN public.st_customer_statement.customer_id IS '客户内部标识，引用 md_customer(id)';
COMMENT ON COLUMN public.fm_receipt.customer_id IS '客户内部标识，引用 md_customer(id)';
COMMENT ON COLUMN public.lg_freight_bill_item.customer_id IS '本行客户内部标识，引用 md_customer(id)';
COMMENT ON COLUMN public.lg_freight_bill_item.project_id IS '本行项目内部标识，引用 md_project(id)';
