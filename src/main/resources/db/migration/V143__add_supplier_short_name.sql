-- 供应商资料增加简称: 用于下拉/单据等空间受限场景展示
ALTER TABLE public.md_supplier
    ADD COLUMN short_name character varying(64);

COMMENT ON COLUMN public.md_supplier.short_name IS '供应商简称(下拉/单据展示用, 非必填)';
