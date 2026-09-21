-- 报单比价: 商品行新增行级备注。
-- 说明:
--   1) 备注为行级自由文本, 跟随商品行一起保存, 用于报单标注;
--   2) 隔断行不携带备注(库中保持 NULL), 与商品字段口径一致;
--   3) 长度上限 255 与单据级 remark 对齐。

ALTER TABLE public.mk_quote_item
    ADD COLUMN remark character varying(255);

COMMENT ON COLUMN public.mk_quote_item.remark IS '商品行备注(隔断行为空)';
