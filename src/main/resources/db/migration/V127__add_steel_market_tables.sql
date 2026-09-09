-- 钢材行情域: Mysteel 行情文章与行情明细
-- 行情独立存表, 不与商品资料(md_material)耦合; 商品匹配在查询层按规则计算

CREATE TABLE public.mk_steel_article (
    id bigint NOT NULL,
    article_url character varying(255) NOT NULL,
    article_date date NOT NULL,
    article_time character varying(8) NOT NULL,
    title character varying(255) NOT NULL,
    period character varying(8) NOT NULL,
    row_count integer NOT NULL,
    market character varying(16) NOT NULL,
    fetched_at timestamp without time zone NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT mk_steel_article_pkey PRIMARY KEY (id),
    CONSTRAINT uk_steel_article_url UNIQUE (article_url)
);

COMMENT ON TABLE public.mk_steel_article IS '钢材行情文章抓取记录';
COMMENT ON COLUMN public.mk_steel_article.article_time IS '行情发布时间 HHmm';
COMMENT ON COLUMN public.mk_steel_article.period IS '时段: 上午/中午/下午';
COMMENT ON COLUMN public.mk_steel_article.row_count IS '解密成功的行情行数';

CREATE TABLE public.mk_steel_quote (
    id bigint NOT NULL,
    article_id bigint NOT NULL,
    market character varying(16) NOT NULL,
    quote_date date NOT NULL,
    period character varying(8) NOT NULL,
    breed character varying(32) NOT NULL,
    spec character varying(32) NOT NULL,
    material character varying(32) NOT NULL,
    factory character varying(64) NOT NULL,
    price numeric(12,2) NOT NULL,
    change_val character varying(16),
    remark character varying(255),
    scraped_at timestamp without time zone NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT mk_steel_quote_pkey PRIMARY KEY (id),
    CONSTRAINT uk_steel_quote_row UNIQUE (quote_date, period, breed, spec, material, factory),
    CONSTRAINT fk_steel_quote_article FOREIGN KEY (article_id)
        REFERENCES public.mk_steel_article(id)
);

COMMENT ON TABLE public.mk_steel_quote IS '钢材行情明细(独立存表, 不关联商品资料)';
COMMENT ON COLUMN public.mk_steel_quote.period IS '时段: 上午/中午/下午';
COMMENT ON COLUMN public.mk_steel_quote.breed IS '品名: 螺纹钢/盘螺/高线/圆钢';
COMMENT ON COLUMN public.mk_steel_quote.spec IS '规格, 如 Φ16 或 Φ16-25';

CREATE INDEX idx_steel_quote_date_period ON public.mk_steel_quote (quote_date, period);
CREATE INDEX idx_steel_quote_match ON public.mk_steel_quote (factory, breed, material, spec);
CREATE INDEX idx_steel_quote_breed ON public.mk_steel_quote (breed);
CREATE INDEX idx_steel_article_date ON public.mk_steel_article (article_date);
