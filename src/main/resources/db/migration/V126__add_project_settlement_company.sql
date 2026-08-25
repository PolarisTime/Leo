ALTER TABLE public.md_project
    ADD COLUMN settlement_company_id bigint,
    ADD COLUMN settlement_company_name character varying(128);

COMMENT ON COLUMN public.md_project.settlement_company_id IS
    '项目默认结算主体，引用 sys_company_setting(id)';

COMMENT ON COLUMN public.md_project.settlement_company_name IS
    '项目默认结算主体名称快照';

UPDATE public.md_project project
SET settlement_company_id = customer.default_settlement_company_id,
    settlement_company_name = customer.default_settlement_company_name
FROM public.md_customer customer
WHERE project.customer_id = customer.id
  AND project.settlement_company_id IS NULL
  AND customer.default_settlement_company_id IS NOT NULL;

CREATE INDEX idx_md_project_settlement_company
    ON public.md_project (settlement_company_id)
    WHERE deleted_flag = false;

ALTER TABLE public.md_project
    ADD CONSTRAINT fk_md_project_settlement_company
        FOREIGN KEY (settlement_company_id)
        REFERENCES public.sys_company_setting(id);
