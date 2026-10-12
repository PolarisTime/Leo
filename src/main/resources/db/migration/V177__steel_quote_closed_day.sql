-- 行情休市日(站点该日未发布行情)标记
--
-- 背景: 覆盖矩阵按"工作日=应有行情"推导, 但法定节假日(如国庆 10-01~10-07)与调休并不服从
-- 周末规则, 前端无法自行判断; 站点"正常响应但没有行情"这一事实只在同步/补数时瞬时可见
-- (QuoteNotPublishedException), 过去用完即弃, 于是节假日在矩阵上一直被显示成"缺失(可同步)",
-- 用户点进去同步只会得到一次失败。这里把该事实按 (数据源, 市场, 日期) 落库, 供日历接口返回,
-- 矩阵据此显示"休市"。该日之后若真的出现行情, 记录会被删除(自愈), 不长期压制真实缺失。

CREATE TABLE public.mk_steel_quote_closed_day (
    id bigint NOT NULL,
    source character varying(16) NOT NULL,
    market character varying(16) NOT NULL,
    quote_date date NOT NULL,
    reason character varying(255),
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT mk_steel_quote_closed_day_pkey PRIMARY KEY (id),
    CONSTRAINT uk_steel_quote_closed_day UNIQUE (source, market, quote_date)
);

COMMENT ON TABLE public.mk_steel_quote_closed_day IS '行情休市日: 站点该日未发布行情(节假日/休市), 由同步与补数过程写入';
COMMENT ON COLUMN public.mk_steel_quote_closed_day.source IS '数据源: MYSTEEL/STEELX';
COMMENT ON COLUMN public.mk_steel_quote_closed_day.market IS '市场: Mysteel 为杭州, 西本为地区名';
COMMENT ON COLUMN public.mk_steel_quote_closed_day.quote_date IS '该日站点未发布行情';
COMMENT ON COLUMN public.mk_steel_quote_closed_day.reason IS '判定原因(站点未发布/全地区无报价), 供排查';

CREATE INDEX idx_steel_quote_closed_day_date ON public.mk_steel_quote_closed_day (quote_date);
