-- 行情第二数据源(西本新干线 steelx2.com): 与 Mysteel 并存。
-- 说明:
--   1) 行情文章/明细新增 source 区分数据源(MYSTEEL/STEELX); 既有数据回填为 MYSTEEL;
--   2) 西本按地区(城市)报价且无品牌, factory 存空串, period 固定"上午";
--   3) 唯一键纳入 source 与 market, 避免不同来源/地区同日同时段互相覆盖;
--   4) 项目资料新增默认取价数据源与地区, 供报单比价/交付核定自动带出。

ALTER TABLE public.mk_steel_article
    ADD COLUMN source character varying(16) NOT NULL DEFAULT 'MYSTEEL';
COMMENT ON COLUMN public.mk_steel_article.source IS '数据源: MYSTEEL/STEELX';

ALTER TABLE public.mk_steel_quote
    ADD COLUMN source character varying(16) NOT NULL DEFAULT 'MYSTEEL';
COMMENT ON COLUMN public.mk_steel_quote.source IS '数据源: MYSTEEL/STEELX';

ALTER TABLE public.mk_steel_quote
    DROP CONSTRAINT uk_steel_quote_row;
ALTER TABLE public.mk_steel_quote
    ADD CONSTRAINT uk_steel_quote_row
        UNIQUE (source, market, quote_date, period, breed, spec, material, factory);

CREATE INDEX idx_steel_quote_source_date ON public.mk_steel_quote (source, quote_date, period);

ALTER TABLE public.mk_steel_article
    DROP CONSTRAINT uk_steel_article_url;
ALTER TABLE public.mk_steel_article
    ADD CONSTRAINT uk_steel_article_source_url UNIQUE (source, article_url);

-- 项目默认取价数据源与地区(西本按地区报价; Mysteel 固定杭州)。
ALTER TABLE public.md_project
    ADD COLUMN quote_source character varying(16),
    ADD COLUMN quote_region character varying(32);
COMMENT ON COLUMN public.md_project.quote_source IS '默认取价数据源: MYSTEEL/STEELX; 空=MYSTEEL';
COMMENT ON COLUMN public.md_project.quote_region IS '默认取价地区(西本城市中文名); 空=杭州';
ALTER TABLE public.md_project
    ADD CONSTRAINT chk_project_quote_source
        CHECK (quote_source IS NULL OR quote_source IN ('MYSTEEL', 'STEELX'));
