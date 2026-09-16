-- 回退报价单明细"按件计量": 移除行计量方式与件数/件重列, 保留单据级 spec_quantity_locked。
-- 说明:
--   1) V145 已在环境执行, 按 Flyway 规范不改写历史迁移, 通过本脚本回退明细列;
--   2) spec_quantity_locked 与既有 locked(锁定参照) 仍是相互独立的两个锁, 不受本脚本影响;
--   3) ton 继续作为明细行唯一权威重量。

ALTER TABLE public.mk_quote_item
    DROP COLUMN IF EXISTS quantity_mode,
    DROP COLUMN IF EXISTS pieces,
    DROP COLUMN IF EXISTS piece_weight_ton;
