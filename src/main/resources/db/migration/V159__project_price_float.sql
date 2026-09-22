-- 项目资料: 网价浮动约定(固定加减幅度, 用于销售订单交付核定按网价赋价)。
-- 说明:
--   1) price_float_mode: ADD=加价, SUBTRACT=减价; NULL/空 表示该项目不启用网价浮动;
--   2) price_float_value: 固定浮动幅度(元/吨), 非负; 加减方向由 mode 决定;
--   3) 由销售订单交付核定按网价计算单价时读取, 属项目级统一约定。

ALTER TABLE public.md_project
    ADD COLUMN price_float_mode character varying(8),
    ADD COLUMN price_float_value numeric(12, 2);

ALTER TABLE public.md_project
    ADD CONSTRAINT chk_project_price_float_mode
        CHECK (price_float_mode IS NULL OR price_float_mode IN ('ADD', 'SUBTRACT')),
    ADD CONSTRAINT chk_project_price_float_value
        CHECK (price_float_value IS NULL OR price_float_value >= 0),
    ADD CONSTRAINT chk_project_price_float_pair
        CHECK ((price_float_mode IS NULL) = (price_float_value IS NULL));

COMMENT ON COLUMN public.md_project.price_float_mode IS '网价浮动方向: ADD加价/SUBTRACT减价, NULL=不浮动';
COMMENT ON COLUMN public.md_project.price_float_value IS '网价固定浮动幅度(元/吨), 非负';
