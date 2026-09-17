-- 供应商经营品牌（品牌取自商品资料去重集合）:
--   1) 后端只存品牌名称字符串, 不建品牌主数据; 前端选项来源为商品资料去重集合;
--   2) 与供应商一对多, 同一供应商下品牌名唯一(uk_supplier_brand);
--   3) brand_name 长度与商品资料品牌保持一致(64), created_at 由数据库默认值填充。

CREATE TABLE public.md_supplier_brand (
    id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    brand_name character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_supplier_brand_supplier FOREIGN KEY (supplier_id) REFERENCES public.md_supplier(id),
    CONSTRAINT uk_supplier_brand UNIQUE (supplier_id, brand_name)
);

ALTER TABLE ONLY public.md_supplier_brand
    ADD CONSTRAINT md_supplier_brand_pkey PRIMARY KEY (id);

CREATE INDEX idx_md_supplier_brand_brand_name ON public.md_supplier_brand USING btree (brand_name);

COMMENT ON TABLE public.md_supplier_brand IS '供应商经营品牌（品牌取自商品资料去重集合）';
COMMENT ON COLUMN public.md_supplier_brand.supplier_id IS '供应商ID, 关联 md_supplier.id';
COMMENT ON COLUMN public.md_supplier_brand.brand_name IS '品牌名称, 取自商品资料去重集合, 同一供应商下唯一';
COMMENT ON COLUMN public.md_supplier_brand.created_at IS '创建时间, 由数据库默认值填充';
