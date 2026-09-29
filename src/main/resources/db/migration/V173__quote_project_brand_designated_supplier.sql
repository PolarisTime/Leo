-- 同品牌多家供应商的取价策略(契约 ③): 项目级品牌行新增「指定优先供应商」与「按供应商的运费覆盖」
-- 背景: 价格表是 (供应商, 品牌) 多行, 而比价的一个品牌列只能有一个现货价。
--   取价规则: 净价 = 现货价 + 该品牌的运费; 同一品牌多家供应商时取净价最低;
--             项目为该品牌指定了优先供应商时, 命中该供应商的价格表则直接取它, 不再比净价;
--             净价并列时取供应商名升序(稳定)。
-- 为什么需要 supplier_freights: mk_quote_project_brand.freight 是品牌级默认运费, 对同一品牌下所有
--   供应商相同, 若只有品牌级运费则"净价最低"恒等价于"现货价最低", 无法表达
--   "A 家报价高但运费低, 净价反而更低" 的真实场景; 因此补一个按供应商的运费覆盖。
-- 说明:
--   supplier_freights 形如 "供应商ID|运费,供应商ID|运费"(与既有 products/categories/
--   designated_brands 的逗号分隔约定一致); 未列出的供应商回退 freight 列(品牌默认运费);
--   应用层负责校验(重复/负数/非法片段 → 422), 数据库只存原样文本。

ALTER TABLE public.mk_quote_project_brand
    ADD COLUMN designated_supplier_id bigint,
    ADD COLUMN supplier_freights text;

COMMENT ON COLUMN public.mk_quote_project_brand.designated_supplier_id IS
    '指定优先供应商ID(项目级配置): 该品牌命中此供应商的价格表时直接取它(不再比净价); 该供应商无该品牌价格表时回退净价最低';
COMMENT ON COLUMN public.mk_quote_project_brand.supplier_freights IS
    '按供应商的运费覆盖, 形如 "supplierId|freight,supplierId|freight"; 未列出的供应商回退 freight 列(品牌默认运费)';

-- 按指定供应商反查项目品牌配置(取价策略在项目级, 但读路径要按 brand_name 命中)
CREATE INDEX idx_quote_project_brand_designated_supplier
    ON public.mk_quote_project_brand (designated_supplier_id)
    WHERE designated_supplier_id IS NOT NULL;
