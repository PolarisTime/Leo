-- 销售订单新增「报单日期」: 取价基准 ORDER(报单日期之后第 N 个交易日) 的锚点。
-- 说明:
--   1) 业务上「报单日期」是客户报单/落单当天, 与送货日期一起决定取哪天的行情;
--   2) 历史单据没有这个日期, 按创建时间所在日期回填 —— 不用 CURRENT_DATE, 否则历史单据会被记成升级当天;
--   3) 新建默认当天(DEFAULT CURRENT_DATE), 前端可编辑修正; 实体侧在请求缺该字段时保持原值。

ALTER TABLE public.so_sales_order
    ADD COLUMN order_date date;

UPDATE public.so_sales_order
SET order_date = created_at::date
WHERE order_date IS NULL;

ALTER TABLE public.so_sales_order
    ALTER COLUMN order_date SET DEFAULT CURRENT_DATE;

ALTER TABLE public.so_sales_order
    ALTER COLUMN order_date SET NOT NULL;

COMMENT ON COLUMN public.so_sales_order.order_date IS '报单日期(客户报单/落单当天); 取价基准 ORDER 的锚点';

-- 列表按报单日期排序/筛选的场景: 与 delivery_date 一样建普通索引(部分索引, 排除软删)。
CREATE INDEX idx_so_sales_order_order_date
    ON public.so_sales_order (order_date)
    WHERE deleted_flag = false;
