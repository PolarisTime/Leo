-- 比价报价单增强: 现货价增加来源供应商; 新增项目级比价配置(品牌/可选商品/指定品牌/兜底)
-- 说明: 供应商名称为快照, 便于单据留存; 项目级配置按 project_id 唯一, 可整体替换

ALTER TABLE public.mk_quote_item_price
    ADD COLUMN supplier_id bigint,
    ADD COLUMN supplier_name character varying(200);

COMMENT ON COLUMN public.mk_quote_item_price.supplier_id IS '现货价来源供应商ID(主数据 supplier)';
COMMENT ON COLUMN public.mk_quote_item_price.supplier_name IS '供应商名称快照';

CREATE TABLE public.mk_quote_project_config (
    id bigint NOT NULL,
    project_id bigint NOT NULL,
    length_premium numeric(10,2) DEFAULT 30 NOT NULL,
    hrb400e_fallback boolean DEFAULT false NOT NULL,
    products text,
    designated_brands text,
    remark character varying(255),
    version bigint DEFAULT 0 NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT mk_quote_project_config_pkey PRIMARY KEY (id),
    CONSTRAINT uk_quote_project_config_project UNIQUE (project_id)
);

COMMENT ON TABLE public.mk_quote_project_config IS '比价项目级配置';
COMMENT ON COLUMN public.mk_quote_project_config.products IS '可选商品键列表(逗号分隔: 类别|材质|规格|长度); 空表示全部可选';
COMMENT ON COLUMN public.mk_quote_project_config.designated_brands IS '指定品牌列表(逗号分隔, 仅报单展示)';

CREATE TABLE public.mk_quote_project_brand (
    id bigint NOT NULL,
    config_id bigint NOT NULL,
    brand_name character varying(64) NOT NULL,
    freight numeric(10,2) DEFAULT 0 NOT NULL,
    categories text,
    sort_order integer DEFAULT 0 NOT NULL,
    CONSTRAINT mk_quote_project_brand_pkey PRIMARY KEY (id),
    CONSTRAINT uk_quote_project_brand UNIQUE (config_id, brand_name),
    CONSTRAINT fk_quote_project_brand_config FOREIGN KEY (config_id)
        REFERENCES public.mk_quote_project_config(id)
);

COMMENT ON TABLE public.mk_quote_project_brand IS '比价项目参与品牌(运费/启用品种)';
COMMENT ON COLUMN public.mk_quote_project_brand.categories IS '启用品种列表(逗号分隔); 空表示全部启用';

CREATE INDEX idx_quote_item_price_supplier ON public.mk_quote_item_price (supplier_id);
