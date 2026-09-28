-- 供应商价格表取消版本语义(契约 4.6 修订 R2)
-- 背景: 价格表不再有"版本"概念, 一个 (供应商, 品牌) 只保留一张当前表。
-- 顺序要求: 必须先把历史多版本归并(软删除较早的行), 再建新的唯一索引, 否则索引会因历史重复行创建失败。
-- 说明:
--   1) released_at / status / effective_from / effective_to 语义作废, 但**不删列**(保留历史数据与 API 形状);
--      展示"更新时间"请统一使用 updated_at;
--   2) status 新行恒为 ACTIVE, 不再有 ARCHIVED 流转; released_at 仅为兼容保留并补默认值;
--   3) "新建更晚版本自动归档旧版"/"同一发布时刻 409" 等语义全部作废, 重复建表由应用层 409 + 本唯一索引兜底。

-- 1) 归并历史多版本: 同一 (supplier_id, brand_name) 仅保留 updated_at(回退 created_at)最新的一行, 其余置 deleted_flag = true
WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY supplier_id, brand_name
               ORDER BY COALESCE(updated_at, created_at) DESC, id DESC
           ) AS row_no
    FROM public.mk_supplier_price_list
    WHERE deleted_flag = false
)
UPDATE public.mk_supplier_price_list target
SET deleted_flag = true
FROM ranked
WHERE target.id = ranked.id
  AND ranked.row_no > 1;

-- 2) 唯一性口径从"每 (供应商, 品牌) 仅一个 ACTIVE 版本"改为"每 (供应商, 品牌) 仅一张未删除价格表"
DROP INDEX IF EXISTS public.uk_supplier_price_list_active;

CREATE UNIQUE INDEX uk_supplier_price_list_supplier_brand
    ON public.mk_supplier_price_list USING btree (supplier_id, brand_name)
    WHERE deleted_flag = false;

-- 3) 放宽已作废列: effective_from / effective_to 允许 NULL; released_at 保留 NOT NULL 但补数据库默认值
ALTER TABLE public.mk_supplier_price_list
    ALTER COLUMN effective_from DROP NOT NULL,
    ALTER COLUMN released_at SET DEFAULT CURRENT_TIMESTAMP;

-- 4) COMMENT 明确"已取消版本语义": 这些列仅为兼容保留, 展示请用 updated_at
COMMENT ON TABLE public.mk_supplier_price_list IS
    '供应商价格表(比价一手价; 已取消版本语义: 一个供应商+品牌仅一张未删除表, 展示更新时间请用 updated_at)';
COMMENT ON COLUMN public.mk_supplier_price_list.released_at IS
    '已取消版本语义, 仅为兼容保留(不再参与取版/筛选), 新行缺省 CURRENT_TIMESTAMP; 展示请用 updated_at';
COMMENT ON COLUMN public.mk_supplier_price_list.effective_from IS
    '已取消版本语义, 仅为兼容保留(允许 NULL, 不参与取版/筛选)';
COMMENT ON COLUMN public.mk_supplier_price_list.effective_to IS
    '已取消版本语义, 仅为兼容保留(允许 NULL, 不参与取版/筛选)';
COMMENT ON COLUMN public.mk_supplier_price_list.status IS
    '已取消版本语义, 仅为兼容保留: 新行恒为 ACTIVE, 不再有 ARCHIVED 流转';
COMMENT ON COLUMN public.mk_supplier_price_list.deleted_flag IS
    '软删除标记, true 表示已删除; 仅 false 的行参与唯一性约束(uk_supplier_price_list_supplier_brand)';
