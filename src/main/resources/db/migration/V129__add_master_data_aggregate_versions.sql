ALTER TABLE public.md_customer
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.md_supplier
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.md_carrier
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.md_warehouse
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.md_project
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.md_material
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.md_material_category
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.sys_company_setting
    ADD COLUMN version bigint NOT NULL DEFAULT 0;
