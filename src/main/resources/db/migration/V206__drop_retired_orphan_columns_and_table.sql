-- 历史遗留收口二：删除已无代码/视图依赖的死列与孤儿表。
--
-- 依据 2026-10-31 收尾核查（.local/len-audit/remaining-checks.md A1/A3）：
--   1) fm_payment_allocation.source_supplier_statement_id：指向退役表 st_supplier_statement 的死外键列
--      （FK 已在 V205 删除），表 0 行、零 Java/视图/函数引用；
--   2) fm_supplier_refund_receipt.purchase_refund_id：指向退役表 po_purchase_refund 的死列
--      （FK 已在 V205 删除），表 0 行、零 Java 引用；列上的 NOT NULL 约束随列一起消失；
--   3) sys_general_setting：V95 已退役的通用设置表，10 行全部 deleted_flag=true，
--      零 Java/XML/YAML/视图/函数/触发器引用、无子表外键 → 孤儿数据整体移除。
-- 三者的备份与依据见 docs/legacy-retired-modules.md。
--
-- 幂等：全部 IF EXISTS。

ALTER TABLE public.fm_payment_allocation
    DROP COLUMN IF EXISTS source_supplier_statement_id;

ALTER TABLE public.fm_supplier_refund_receipt
    DROP COLUMN IF EXISTS purchase_refund_id;

DROP TABLE IF EXISTS public.sys_general_setting;
