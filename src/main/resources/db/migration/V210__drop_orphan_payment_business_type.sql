-- V210: 删除 fm_payment 的孤立 NOT NULL 列 business_type。
--
-- 背景：eb9c985b「移除收款/付款的废弃兼容字段并收敛双列结构」删除了 Payment 实体对
-- business_type 的映射（Payment.java 现仅保留注释说明），但**没有同步删除该列**。
-- 该列 NOT NULL 且无默认值，导致 INSERT INTO fm_payment 永远缺列：
--   null value in column "business_type" of relation "fm_payment" violates not-null constraint
-- 生产表现为 POST /payments 100% 返回 422「字段长度或数值超过允许范围」，付款单无法创建。
-- （2026-11-01 联调发现；fm_receipt 无该列，收款单不受影响。）
--
-- 依赖处理：global_search_document 视图仍引用 payment.business_type（summary / search_text
-- 的 concat_ws 参数）。按 V208 同一套路，先把该引用原位替换为 payment.counterparty_type
-- （往来方类型，NOT NULL；业务类型已无数据来源），再删列。
--
-- 幂等：replace 在列删除后为 no-op；CREATE OR REPLACE 输出列形状不变；DROP COLUMN IF EXISTS。

DO $$
DECLARE
    view_definition text;
BEGIN
    view_definition := pg_get_viewdef('public.global_search_document'::regclass, true);
    view_definition := replace(
            view_definition,
            'payment.business_type',
            'payment.counterparty_type');
    EXECUTE 'CREATE OR REPLACE VIEW public.global_search_document AS ' || view_definition;
END
$$;

ALTER TABLE public.fm_payment
    DROP COLUMN IF EXISTS business_type;
