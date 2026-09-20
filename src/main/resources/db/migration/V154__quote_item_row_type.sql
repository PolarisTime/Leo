-- 报单比价: 明细行新增 row_type(商品行/隔断行), 并放开隔断行无商品字段的 NOT NULL 约束。
-- 说明:
--   1) row_type: PRODUCT(商品行, 默认) / SEPARATOR(隔断行, 仅作视觉分组);
--   2) 历史行全部回填为 PRODUCT, 保持既有行为不变;
--   3) 隔断行不携带 category/material/spec/length, 故这四列放开 NOT NULL;
--      仍由服务层按 row_type 强制商品行必填, 避免脏数据;
--   4) 长度约束(length=16)保留, 隔断行写入 NULL 不受影响。

ALTER TABLE public.mk_quote_item
    ADD COLUMN row_type character varying(16) DEFAULT 'PRODUCT'::character varying NOT NULL;

COMMENT ON COLUMN public.mk_quote_item.row_type IS '行类型: PRODUCT(商品行)/SEPARATOR(隔断行)';

ALTER TABLE public.mk_quote_item
    ALTER COLUMN category DROP NOT NULL,
    ALTER COLUMN material DROP NOT NULL,
    ALTER COLUMN spec DROP NOT NULL,
    ALTER COLUMN length DROP NOT NULL;
