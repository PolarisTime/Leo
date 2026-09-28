-- 供应商价格表(比价一手价): 版本头 + 规格条目 + 整表加减留痕 + 比价单现货价来源扩展
-- 说明:
--   1) 价格表属比价一手价, 用 mk_ 前缀; 条目键 = 供应商 + 品牌(产地) + 材质 + 直径 + 定尺;
--   2) price IS NULL 表示"不报价", 与 0 元严格区分, 不得用 0 表达不报价;
--   3) 同一 (supplier_id, brand_name) 同一时刻仅一个 ACTIVE 版本, 由部分唯一索引兜底;
--   4) 既有 mk_quote_item_price 行一律保持 price_source='MANUAL'(单据覆盖值), 本迁移不改写任何既有 spot_price。

CREATE TABLE public.mk_supplier_price_list (
    id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    supplier_name character varying(200) NOT NULL,
    brand_name character varying(64) NOT NULL,
    released_at timestamp(0) without time zone NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    warehouse character varying(64),
    remark character varying(255),
    version bigint DEFAULT 0 NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT mk_supplier_price_list_pkey PRIMARY KEY (id),
    CONSTRAINT fk_supplier_price_list_supplier FOREIGN KEY (supplier_id) REFERENCES public.md_supplier(id),
    CONSTRAINT ck_supplier_price_list_effective_range CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_supplier_price_list_status CHECK (status::text IN ('ACTIVE', 'ARCHIVED'))
);

-- 同一供应商 + 品牌仅一个生效版本
CREATE UNIQUE INDEX uk_supplier_price_list_active
    ON public.mk_supplier_price_list USING btree (supplier_id, brand_name)
    WHERE deleted_flag = false AND status::text = 'ACTIVE';

-- 取版口径: 按 (供应商, 品牌) 在给定时刻之前取 released_at 最大的未删除版本
CREATE INDEX idx_supplier_price_list_lookup
    ON public.mk_supplier_price_list USING btree (supplier_id, brand_name, released_at DESC)
    WHERE deleted_flag = false;

CREATE INDEX idx_supplier_price_list_brand_released
    ON public.mk_supplier_price_list USING btree (brand_name, released_at DESC)
    WHERE deleted_flag = false;

COMMENT ON TABLE public.mk_supplier_price_list IS '供应商价格表版本头(比价一手价, 一日可多版)';
COMMENT ON COLUMN public.mk_supplier_price_list.supplier_id IS '供应商ID, 关联 md_supplier.id';
COMMENT ON COLUMN public.mk_supplier_price_list.supplier_name IS '供应商名称快照(创建时写入, 不随主数据改名漂移)';
COMMENT ON COLUMN public.mk_supplier_price_list.brand_name IS '品牌/产地, 取自 md_supplier_brand 或商品资料去重集合, 不建品牌主数据';
COMMENT ON COLUMN public.mk_supplier_price_list.released_at IS '发布时刻(精确到分, 支持一日多版), 取版时按此列比较';
COMMENT ON COLUMN public.mk_supplier_price_list.effective_from IS '生效起始日期, 缺省 = released_at 当日';
COMMENT ON COLUMN public.mk_supplier_price_list.effective_to IS '生效截止日期, NULL 表示长期有效';
COMMENT ON COLUMN public.mk_supplier_price_list.status IS '版本状态: ACTIVE 生效 / ARCHIVED 已归档(仅历史留痕)';
COMMENT ON COLUMN public.mk_supplier_price_list.warehouse IS '仓库(展示用, 如 东恒库/钢联新安库)';
COMMENT ON COLUMN public.mk_supplier_price_list.version IS '乐观锁版本号';
COMMENT ON COLUMN public.mk_supplier_price_list.deleted_flag IS '软删除标记, true 表示已删除; 仅 false 的行参与取版与唯一性约束';

CREATE TABLE public.mk_supplier_price_item (
    id bigint NOT NULL,
    list_id bigint NOT NULL,
    category character varying(16) NOT NULL,
    material character varying(16) NOT NULL,
    spec integer NOT NULL,
    length character varying(16) NOT NULL,
    price numeric(12,2),
    price_status character varying(16) DEFAULT 'NORMAL'::character varying NOT NULL,
    remark character varying(255),
    sort_order integer DEFAULT 0 NOT NULL,
    CONSTRAINT mk_supplier_price_item_pkey PRIMARY KEY (id),
    CONSTRAINT uk_supplier_price_item_key UNIQUE (list_id, category, material, spec, length),
    CONSTRAINT fk_supplier_price_item_list FOREIGN KEY (list_id) REFERENCES public.mk_supplier_price_list(id),
    CONSTRAINT ck_supplier_price_item_spec CHECK (spec > 0),
    CONSTRAINT ck_supplier_price_item_price CHECK (price IS NULL OR price >= 0),
    CONSTRAINT ck_supplier_price_item_status CHECK (price_status::text IN ('NORMAL', 'PENDING', 'BUNDLED', 'NEGOTIABLE', 'OUT_OF_STOCK'))
);

CREATE INDEX idx_supplier_price_item_list ON public.mk_supplier_price_item USING btree (list_id);

COMMENT ON TABLE public.mk_supplier_price_item IS '供应商价格表条目(稀疏矩阵: price 为空表示不报价)';
COMMENT ON COLUMN public.mk_supplier_price_item.list_id IS '所属价格表版本ID, 关联 mk_supplier_price_list.id';
COMMENT ON COLUMN public.mk_supplier_price_item.category IS '类别, 与 mk_quote_item.category 对齐';
COMMENT ON COLUMN public.mk_supplier_price_item.material IS '材质, 长度已拆分, 不得含 9米/12米';
COMMENT ON COLUMN public.mk_supplier_price_item.spec IS '规格 = 直径 mm, 必须大于 0';
COMMENT ON COLUMN public.mk_supplier_price_item.length IS '定尺; 无定尺概念时存空串, 与 mk_quote_item.length 对齐';
COMMENT ON COLUMN public.mk_supplier_price_item.price IS '单价(元/吨, 含税出厂价); NULL = 不报价, 与 0 元严格区分';
COMMENT ON COLUMN public.mk_supplier_price_item.price_status IS '报价状态: NORMAL 正常/PENDING 在途待卸/BUNDLED 搭配/NEGOTIABLE 价格单议/OUT_OF_STOCK 无货';
COMMENT ON COLUMN public.mk_supplier_price_item.sort_order IS '展示排序号';

CREATE TABLE public.mk_supplier_price_adjustment (
    id bigint NOT NULL,
    list_id bigint NOT NULL,
    mode character varying(8) NOT NULL,
    amount numeric(12,2) NOT NULL,
    item_count integer DEFAULT 0 NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT mk_supplier_price_adjustment_pkey PRIMARY KEY (id),
    CONSTRAINT fk_supplier_price_adjustment_list FOREIGN KEY (list_id) REFERENCES public.mk_supplier_price_list(id),
    CONSTRAINT ck_supplier_price_adjustment_mode CHECK (mode::text IN ('ADD', 'SUBTRACT')),
    CONSTRAINT ck_supplier_price_adjustment_amount CHECK (amount > 0)
);

CREATE INDEX idx_supplier_price_adjustment_list ON public.mk_supplier_price_adjustment USING btree (list_id, created_at DESC);

COMMENT ON TABLE public.mk_supplier_price_adjustment IS '供应商价格表整表/选区加减留痕头';
COMMENT ON COLUMN public.mk_supplier_price_adjustment.mode IS '加减方向: ADD 加 / SUBTRACT 减';
COMMENT ON COLUMN public.mk_supplier_price_adjustment.amount IS '加减金额(元/吨), 必须大于 0';
COMMENT ON COLUMN public.mk_supplier_price_adjustment.item_count IS '本次实际受影响条目数(不报价条目不计入)';

CREATE TABLE public.mk_supplier_price_adjustment_item (
    id bigint NOT NULL,
    adjustment_id bigint NOT NULL,
    item_id bigint NOT NULL,
    price_before numeric(12,2) NOT NULL,
    price_after numeric(12,2) NOT NULL,
    CONSTRAINT mk_supplier_price_adjustment_item_pkey PRIMARY KEY (id),
    CONSTRAINT fk_supplier_price_adjustment_item_head FOREIGN KEY (adjustment_id) REFERENCES public.mk_supplier_price_adjustment(id),
    CONSTRAINT ck_supplier_price_adjustment_item_before CHECK (price_before >= 0),
    CONSTRAINT ck_supplier_price_adjustment_item_after CHECK (price_after >= 0)
);

CREATE INDEX idx_supplier_price_adjustment_item_head
    ON public.mk_supplier_price_adjustment_item USING btree (adjustment_id);

COMMENT ON TABLE public.mk_supplier_price_adjustment_item IS '供应商价格表加减留痕明细(前价/后价)';
COMMENT ON COLUMN public.mk_supplier_price_adjustment_item.item_id IS '被调整的条目ID, 关联 mk_supplier_price_item.id; 不加外键以保留留痕';
COMMENT ON COLUMN public.mk_supplier_price_adjustment_item.price_before IS '调整前单价';
COMMENT ON COLUMN public.mk_supplier_price_adjustment_item.price_after IS '调整后单价(不为负, 不做静默截断)';

-- 比价单行×品牌现货价扩展: 记录来源与来源价格表版本(供 UI 标记 / 恢复价格表价)
ALTER TABLE public.mk_quote_item_price
    ADD COLUMN price_source character varying(16) DEFAULT 'MANUAL'::character varying NOT NULL,
    ADD COLUMN price_list_id bigint,
    ADD COLUMN price_list_released_at timestamp without time zone;

ALTER TABLE public.mk_quote_item_price
    ADD CONSTRAINT ck_quote_item_price_source CHECK (price_source::text IN ('MANUAL', 'PRICE_LIST'));

CREATE INDEX idx_quote_item_price_list ON public.mk_quote_item_price USING btree (price_list_id);

COMMENT ON COLUMN public.mk_quote_item_price.price_source IS '价格来源: MANUAL 单据手填/覆盖(含全部历史数据) / PRICE_LIST 由供应商价格表推导或固化';
COMMENT ON COLUMN public.mk_quote_item_price.price_list_id IS '来源价格表版本ID(快照, 不建外键); price_source=PRICE_LIST 时非空';
COMMENT ON COLUMN public.mk_quote_item_price.price_list_released_at IS '来源价格表版本的发布时刻快照';
