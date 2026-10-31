-- V207: 删除 6 个 deprecated 列（代码侧已零读写）。
--
-- 背景：
--   这些列均为历史兼容列，DB 中早已带「已废弃 / DEPRECATED」列注释，Java 实体与查询已不映射。
--   逐列核对（LSP find_symbol / find_referencing_symbols，2026-10-31）：
--     * Project(md_project) 无 price_closed_day_prev_settle 字段（仅 Javadoc 提及旧列）；
--     * SalesOrder(so_sales_order) 无 price_closed_settle_prev / sales_mode 字段；
--     * FreightBill(lg_freight_bill) 无 source_sales_order_id 字段；
--     * SalesOutbound(so_sales_outbound) 无 source_freight_bill_id 字段；
--     * FreightStatementItem(st_freight_statement_item) 无 source_sales_outbound_item_id 字段。
--
-- 逐列（类型 / 默认值 / 注释来源迁移）：
--   1) md_project.price_closed_day_prev_settle            boolean NOT NULL DEFAULT false
--      V195 起 deprecated（由 price_closed_settle_direction + price_closed_settle_days 取代）；
--   2) so_sales_order.price_closed_settle_prev            boolean
--      V195 起 deprecated（同上）；
--   3) so_sales_order.sales_mode                          varchar(16) NOT NULL DEFAULT 'NORMAL'
--      V74 起 deprecated（销售订单仅支持入库后销售）；
--   4) lg_freight_bill.source_sales_order_id              bigint
--      V73 起 deprecated（物流模块不再引用销售订单）；
--   5) so_sales_outbound.source_freight_bill_id           bigint
--      V73 起 deprecated（销售出库不再引用物流单）；
--   6) st_freight_statement_item.source_sales_outbound_item_id bigint
--      V73 起 deprecated（物流对账仅引用物流单明细）。
--
-- 依赖核对（2026-10-31，pg_depend / pg_constraint / pg_index）：
--   6 列上无索引、无视图、无触发器、无扩展统计；列级 NOT NULL
--   （md_project_price_closed_day_prev_settle_not_null、so_sales_order_sales_mode_not_null）
--   与列级 CHECK chk_so_sales_order_sales_mode_v74（conkey = {sales_mode}，
--   CHECK (((sales_mode)::text = 'NORMAL'::text))）都随 DROP COLUMN 自动删除，
--   无需显式 DROP CONSTRAINT。
--
-- 幂等性：
--   ALTER TABLE ... DROP COLUMN IF EXISTS，可重复执行；Flyway 事务包裹，失败整体回滚。
--
-- 回滚（只能恢复结构，历史取值不可恢复；这些列 deprecated 后已无业务写入）：
--   ALTER TABLE public.md_project ADD COLUMN price_closed_day_prev_settle boolean NOT NULL DEFAULT false;
--   ALTER TABLE public.so_sales_order ADD COLUMN price_closed_settle_prev boolean;
--   ALTER TABLE public.so_sales_order ADD COLUMN sales_mode varchar(16) NOT NULL DEFAULT 'NORMAL';
--   ALTER TABLE public.so_sales_order ADD CONSTRAINT chk_so_sales_order_sales_mode_v74
--       CHECK (sales_mode::text = 'NORMAL'::text);
--   ALTER TABLE public.lg_freight_bill ADD COLUMN source_sales_order_id bigint;
--   ALTER TABLE public.so_sales_outbound ADD COLUMN source_freight_bill_id bigint;
--   ALTER TABLE public.st_freight_statement_item ADD COLUMN source_sales_outbound_item_id bigint;
--
-- 执行前数据核对（只读，建议先跑；任何非 0 都说明还有历史数据取值会被永久丢弃）：
--   SELECT count(*) FROM public.md_project WHERE price_closed_day_prev_settle IS DISTINCT FROM false;
--   SELECT count(*) FROM public.so_sales_order WHERE price_closed_settle_prev IS NOT NULL;
--   SELECT count(*) FROM public.so_sales_order WHERE sales_mode IS DISTINCT FROM 'NORMAL';
--   SELECT count(*) FROM public.lg_freight_bill WHERE source_sales_order_id IS NOT NULL;
--   SELECT count(*) FROM public.so_sales_outbound WHERE source_freight_bill_id IS NOT NULL;
--   SELECT count(*) FROM public.st_freight_statement_item WHERE source_sales_outbound_item_id IS NOT NULL;

ALTER TABLE public.md_project
    DROP COLUMN IF EXISTS price_closed_day_prev_settle;

ALTER TABLE public.so_sales_order
    DROP COLUMN IF EXISTS price_closed_settle_prev;

ALTER TABLE public.so_sales_order
    DROP COLUMN IF EXISTS sales_mode;

ALTER TABLE public.lg_freight_bill
    DROP COLUMN IF EXISTS source_sales_order_id;

ALTER TABLE public.so_sales_outbound
    DROP COLUMN IF EXISTS source_freight_bill_id;

ALTER TABLE public.st_freight_statement_item
    DROP COLUMN IF EXISTS source_sales_outbound_item_id;
