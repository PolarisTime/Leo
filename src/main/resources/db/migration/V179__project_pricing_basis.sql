-- 项目取价基准(按日期自动取价)与销售订单取价快照。
-- 说明:
--   1) 基准日期 price_date_basis: DELIVERY 送货日期当天 / ORDER 报单日期之后第 X 个交易日 / MANUAL 每次手工选择;
--   2) 报单日为休市日时: price_closed_day_prev_settle=true 按前一个交易日结算, false(默认) 向后取第 X 个交易日;
--   3) 时段策略 price_period_policy: AUTO 当日最新时段 / FIXED 固定时段 / MANUAL 每次手工选择;
--   4) X 用「交易日」口径, 由行情日历判定(法定节假日与调休不服从周末直觉), 因此区间校验 0..30;
--   5) 交付核定把「基准日 + 实际取价日 + 实际时段 + 生效规则」快照到销售订单, 历史单据不因配置变更而失真。

ALTER TABLE public.md_project
    ADD COLUMN price_date_basis character varying(16) DEFAULT 'DELIVERY'::character varying NOT NULL,
    ADD COLUMN price_date_offset_days integer DEFAULT 0 NOT NULL,
    ADD COLUMN price_closed_day_prev_settle boolean DEFAULT false NOT NULL,
    ADD COLUMN price_period_policy character varying(16) DEFAULT 'AUTO'::character varying NOT NULL,
    ADD COLUMN price_period_fixed character varying(8);

COMMENT ON COLUMN public.md_project.price_date_basis IS '取价基准: DELIVERY送货日期/ORDER报单日期+N个交易日/MANUAL每次手工选择';
COMMENT ON COLUMN public.md_project.price_date_offset_days IS '报单日基准的偏移交易日数(0..30), 仅 ORDER 有意义';
COMMENT ON COLUMN public.md_project.price_closed_day_prev_settle IS '报单日为休市日时是否按前一个交易日结算, 仅 ORDER 有意义';
COMMENT ON COLUMN public.md_project.price_period_policy IS '时段策略: AUTO当日最新/FIXED固定/MANUAL每次手工选择';
COMMENT ON COLUMN public.md_project.price_period_fixed IS '固定取价时段(上午/中午/下午), 仅 FIXED 时有值';

ALTER TABLE public.md_project
    ADD CONSTRAINT chk_project_price_date_basis
        CHECK (price_date_basis IN ('DELIVERY', 'ORDER', 'MANUAL')),
    ADD CONSTRAINT chk_project_price_date_offset_days
        CHECK (price_date_offset_days >= 0 AND price_date_offset_days <= 30),
    ADD CONSTRAINT chk_project_price_period_policy
        CHECK (price_period_policy IN ('AUTO', 'FIXED', 'MANUAL')),
    ADD CONSTRAINT chk_project_price_period_fixed
        CHECK (price_period_fixed IS NULL OR price_period_fixed IN ('上午', '中午', '下午'));

-- 销售订单: 取价快照(交付核定写入, 手工/自动取价都记, 便于事后还原当天按哪个交易日哪个时段赋价)。
ALTER TABLE public.so_sales_order
    ADD COLUMN price_target_date date,
    ADD COLUMN price_quote_date date,
    ADD COLUMN price_quote_period character varying(8),
    ADD COLUMN price_date_basis character varying(16),
    ADD COLUMN price_date_offset_days integer,
    ADD COLUMN price_closed_settle_prev boolean;

COMMENT ON COLUMN public.so_sales_order.price_target_date IS '取价基准日(送货日期/报单日期/手工日期, 快照)';
COMMENT ON COLUMN public.so_sales_order.price_quote_date IS '实际取价日(交易日历回退后, 快照)';
COMMENT ON COLUMN public.so_sales_order.price_quote_period IS '实际取价时段(上午/中午/下午, 快照)';
COMMENT ON COLUMN public.so_sales_order.price_date_basis IS '本次生效的取价基准(快照, 可能是单据级覆盖值)';
COMMENT ON COLUMN public.so_sales_order.price_date_offset_days IS '本次生效的偏移交易日数(快照)';
COMMENT ON COLUMN public.so_sales_order.price_closed_settle_prev IS '本次生效的休市结算开关(快照)';

ALTER TABLE public.so_sales_order
    ADD CONSTRAINT chk_sales_order_price_date_basis
        CHECK (price_date_basis IS NULL OR price_date_basis IN ('DELIVERY', 'ORDER', 'MANUAL')),
    ADD CONSTRAINT chk_sales_order_price_quote_period
        CHECK (price_quote_period IS NULL OR price_quote_period IN ('上午', '中午', '下午')),
    ADD CONSTRAINT chk_sales_order_price_date_offset_days
        CHECK (price_date_offset_days IS NULL OR (price_date_offset_days >= 0 AND price_date_offset_days <= 30));
