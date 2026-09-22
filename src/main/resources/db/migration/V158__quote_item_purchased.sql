-- 报单比价: 商品行新增「已采购」标记。
-- 说明:
--   1) 勾选行标记为已采购后, 该行「报单吨位」之后的品牌价格列(网价/现货/差价/简称)在前端整体遮蔽且不可编辑;
--   2) 标记为行级布尔, 跟随商品行一起保存, 刷新后保留;
--   3) 隔断行不携带该标记(库中保持 false), 与商品字段口径一致。

ALTER TABLE public.mk_quote_item
    ADD COLUMN purchased boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN public.mk_quote_item.purchased IS '是否已采购(商品行标记, 隔断行恒为 false)';
