-- 比价报价单: 单据头 + 品牌(运费) + 商品行 + 行×品牌现货价
-- 说明: 关联采购订单关系后续再加; 先落库单据与价格

CREATE TABLE public.mk_quote_sheet (
    id bigint NOT NULL,
    sheet_no character varying(64) NOT NULL,
    name character varying(64) NOT NULL,
    project_id bigint,
    project_name character varying(200),
    order_date date NOT NULL,
    ref_date date NOT NULL,
    ref_period character varying(32) NOT NULL,
    length_premium numeric(10,2) DEFAULT 30 NOT NULL,
    locked boolean DEFAULT false NOT NULL,
    status character varying(16) DEFAULT '报价'::character varying NOT NULL,
    remark character varying(255),
    version bigint DEFAULT 0 NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT mk_quote_sheet_pkey PRIMARY KEY (id),
    CONSTRAINT uk_quote_sheet_no UNIQUE (sheet_no)
);

COMMENT ON TABLE public.mk_quote_sheet IS '比价报价单(批次)';
COMMENT ON COLUMN public.mk_quote_sheet.ref_period IS '参照时段, 如 9:30 上午';
COMMENT ON COLUMN public.mk_quote_sheet.length_premium IS '12米加价(元/吨, 仅螺纹钢生效)';

CREATE TABLE public.mk_quote_sheet_brand (
    id bigint NOT NULL,
    sheet_id bigint NOT NULL,
    brand_name character varying(64) NOT NULL,
    freight numeric(10,2) DEFAULT 0 NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    CONSTRAINT mk_quote_sheet_brand_pkey PRIMARY KEY (id),
    CONSTRAINT uk_quote_sheet_brand UNIQUE (sheet_id, brand_name),
    CONSTRAINT fk_quote_sheet_brand_sheet FOREIGN KEY (sheet_id) REFERENCES public.mk_quote_sheet(id)
);

COMMENT ON TABLE public.mk_quote_sheet_brand IS '报价单品牌与运费';

CREATE TABLE public.mk_quote_item (
    id bigint NOT NULL,
    sheet_id bigint NOT NULL,
    line_no integer NOT NULL,
    category character varying(16) NOT NULL,
    material character varying(16) NOT NULL,
    spec integer NOT NULL,
    length character varying(16) NOT NULL,
    ton numeric(18,8),
    CONSTRAINT mk_quote_item_pkey PRIMARY KEY (id),
    CONSTRAINT uk_quote_item_line UNIQUE (sheet_id, line_no),
    CONSTRAINT fk_quote_item_sheet FOREIGN KEY (sheet_id) REFERENCES public.mk_quote_sheet(id)
);

COMMENT ON TABLE public.mk_quote_item IS '报价单商品行';

CREATE TABLE public.mk_quote_item_price (
    id bigint NOT NULL,
    item_id bigint NOT NULL,
    brand_name character varying(64) NOT NULL,
    spot_price numeric(12,2),
    CONSTRAINT mk_quote_item_price_pkey PRIMARY KEY (id),
    CONSTRAINT uk_quote_item_price UNIQUE (item_id, brand_name),
    CONSTRAINT fk_quote_item_price_item FOREIGN KEY (item_id) REFERENCES public.mk_quote_item(id)
);

COMMENT ON TABLE public.mk_quote_item_price IS '报价单行×品牌现货价';

CREATE INDEX idx_quote_sheet_order_date ON public.mk_quote_sheet (order_date);
CREATE INDEX idx_quote_sheet_project ON public.mk_quote_sheet (project_id);
CREATE INDEX idx_quote_item_sheet ON public.mk_quote_item (sheet_id);
CREATE INDEX idx_quote_item_price_item ON public.mk_quote_item_price (item_id);
