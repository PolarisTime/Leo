-- V215: 删除报单商品行的采购订单引用三列（mk_quote_item.purchase_order_id / purchase_order_no /
--       purchase_order_item_id）。
--
-- 背景（2026-11-12「报单比价模块与采购订单彻底解耦」）：
--   三列由 V162（purchase_order_id / purchase_order_no）与 V164（purchase_order_item_id）引入，
--   用于「报单吨位扣减采购订单已开吨位」的订单级/明细行级核算。解耦后：
--     * 实体 QuoteSheetItem 不再映射这三列（字段与 isPurchased() 派生方法已删除）；
--     * QuoteSheetRequest/QuoteSheetResponse 不再收发采购订单字段；
--     * QuoteSheetRepository 的单条汇总 SQL（UNION ALL 两段口径）已删除；
--   因此三列在代码侧零读写。
--
-- 依赖核对（2026-11-12，pg_indexes / pg_constraint）：
--   * purchase_order_id 上有 idx_quote_item_purchase_order；
--   * purchase_order_item_id 上有 idx_quote_item_purchase_order_item；
--   * 两列均无外键（V162/V164 明确采用「计划态引用、不加 FK」），无视图/触发器/扩展统计依赖。
--   两个索引随对应 DROP COLUMN 自动删除，无需显式 DROP INDEX。
--
-- 数据处置：删除前带采购列的 26 行已备份到
--   db-backups/price-compare-decouple-20261112/mk_quote_item_purchase_columns.csv
--   （mk_quote_item 全量 214 行另存 mk_quote_item_full.csv；校验和见同目录 SHA256SUMS，已验证 OK）。
--
-- 幂等：DROP COLUMN IF EXISTS，可重复执行；Flyway 事务包裹，失败整体回滚。
--
-- 回滚（只能恢复结构，取值需从上述 CSV 回灌）：
--   ALTER TABLE public.mk_quote_item ADD COLUMN purchase_order_id bigint;
--   ALTER TABLE public.mk_quote_item ADD COLUMN purchase_order_no character varying(64);
--   ALTER TABLE public.mk_quote_item ADD COLUMN purchase_order_item_id bigint;
--   CREATE INDEX idx_quote_item_purchase_order ON public.mk_quote_item (purchase_order_id);
--   CREATE INDEX idx_quote_item_purchase_order_item ON public.mk_quote_item (purchase_order_item_id);

ALTER TABLE public.mk_quote_item
    DROP COLUMN IF EXISTS purchase_order_id,
    DROP COLUMN IF EXISTS purchase_order_no,
    DROP COLUMN IF EXISTS purchase_order_item_id;
